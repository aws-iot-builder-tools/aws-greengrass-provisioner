package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.DeviceTesterLogMessageType;
import com.awslabs.aws.greengrass.provisioner.data.arguments.TestArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.aws.iot.resultsiterator.helpers.interfaces.JsonHelper;
import com.awslabs.aws.iot.resultsiterator.helpers.v2.interfaces.V2IamHelper;
import com.jcraft.jsch.Session;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.GroupInformation;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BasicGroupTestHelper implements GroupTestHelper {
    private static final String RUNTIME_LOG = "runtime.log";
    private static final String FULL_RUNTIME_LOG_PATH = "/greengrass/ggc/var/log/system/" + RUNTIME_LOG;
    private static final String DAEMON_SEARCH_STRING = "bin/daemon";
    private static final String PROXY_SEARCH_STRING = "tmp/greenlight/proxy/proxy";
    private static final String TAIL_FOLLOW_COMMAND = "tail -F " + FULL_RUNTIME_LOG_PATH;
    private static final int SSH_TIMEOUT_IN_MINUTES = 45;
    private static final String DEVICE_POOL_ID = "DevicePool";
    private static final String WINDOWS_DEVICE_TESTER_URL = "https://d232ctwt5kahio.cloudfront.net/greengrass/devicetester_greengrass_win_1.3.2.zip";
    private static final String LINUX_DEVICE_TESTER_URL = "https://d232ctwt5kahio.cloudfront.net/greengrass/devicetester_greengrass_linux_1.3.2.zip";
    private static final String MAC_DEVICE_TESTER_URL = "https://d232ctwt5kahio.cloudfront.net/greengrass/devicetester_greengrass_mac_1.3.2.zip";
    private static final String SSH_CONNECTED_MESSAGE = "Connected to device under test";
    private static final String SSH_TIMED_OUT_MESSAGE = "SSH connection timed out, device under test may not be ready yet...";
    private static final String SSH_CONNECTION_REFUSED_MESSAGE = "SSH connection refused, device under test may not be ready yet...";
    private static final String SSH_ERROR_MESSAGE = "There was an SSH error [{}]";
    private static final String VAR_LIB_GGQ = "/var/lib/GGQ";
    private final Logger log = LoggerFactory.getLogger(BasicGroupTestHelper.class);
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    GGVariables ggVariables;
    @Inject
    TestArgumentHelper testArgumentHelper;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    AwsHelper awsHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    V2IamHelper iamHelper;
    @Inject
    ProcessHelper processHelper;
    @Inject
    DeviceTesterHelper deviceTesterHelper;
    @Inject
    SshHelper sshHelper;
    private Option<String> optionalCurrentRunningTest = Option.none();

    @Inject
    public BasicGroupTestHelper() {
    }

    @Override
    public void execute(TestArguments testArguments) {
        LocalDateTime testStartLocalDateTime = LocalDateTime.now();

        if (testArguments.deviceUnderTest == null) {
            throw new RuntimeException("No device specified");
        }

        Optional<String> optionalUrlForDeviceTester = Optional.empty();

        if (SystemUtils.IS_OS_WINDOWS) optionalUrlForDeviceTester = Optional.of(WINDOWS_DEVICE_TESTER_URL);
        if (SystemUtils.IS_OS_MAC) optionalUrlForDeviceTester = Optional.of(MAC_DEVICE_TESTER_URL);
        if (SystemUtils.IS_OS_LINUX) optionalUrlForDeviceTester = Optional.of(LINUX_DEVICE_TESTER_URL);

        if (!optionalUrlForDeviceTester.isPresent()) {
            throw new RuntimeException("Could not determine host operating system");
        }

        String urlForDeviceTester = optionalUrlForDeviceTester.get();

        Optional<GroupInformation> optionalGroupInformation = greengrassHelper.getGroupInformation(testArguments.groupName);

        if (!optionalGroupInformation.isPresent()) {
            throw new RuntimeException("Group [" + testArguments.groupName + "] not found");
        }

        GroupInformation groupInformation = optionalGroupInformation.get();

        File deviceTesterDirectory = null;

        // Download and extract the tester or use the existing one
        if (testArguments.deviceTesterLocation != null) {
            // It is downloaded already
            File deviceTesterLocation = new File(testArguments.deviceTesterLocation);

            if (!deviceTesterLocation.exists()) {
                throw new RuntimeException(String.format("Device Tester could not be found at the specified location [%s]", testArguments.deviceTesterLocation));
            }

            if (deviceTesterLocation.isFile()) {
                // It's the archive, we need to extract it
                deviceTesterDirectory = Try.of(() -> extractDeviceTester(deviceTesterLocation)).get();
            }
        } else {
            File deviceTesterZip = Try.of(() -> ioHelper.getTempFile("devicetester", "zip")).get();
            log.info("Downloading Device Tester to [{}] ...", deviceTesterZip.getAbsolutePath());
            // CloudFront requires the referer to be filled in
            Try.run(() -> ioHelper.download(urlForDeviceTester, deviceTesterZip, Optional.of("https://aws.amazon.com/greengrass/device-tester/"))).get();
            deviceTesterDirectory = Try.of(() -> extractDeviceTester(deviceTesterZip)).get();
        }

        // Create the <AWS Account #>.<Region>.CoreAndGroupInfo.json file for /var/lib/GGQ on the device
        log.info("Download and extraction of Device Tester is complete");

        // Connect to the device under test via SSH
        Session session = null;

        try {
            session = sshHelper.getSshSession(testArguments.deviceUnderTest,
                    testArguments.user,
                    SSH_CONNECTED_MESSAGE,
                    SSH_TIMED_OUT_MESSAGE,
                    SSH_CONNECTION_REFUSED_MESSAGE,
                    SSH_ERROR_MESSAGE,
                    SSH_TIMEOUT_IN_MINUTES,
                    TimeUnit.MINUTES);

            // Create a final version of this variable so it can be used in lambdas
            final Session finalSession = session;

            // Clear out the GGQ directory
            if (testArguments.clean) {
                log.info("Cleaning the {} directory", VAR_LIB_GGQ);
                Try.of(() -> ioHelper.runCommand(finalSession, String.join(" ", "sudo rm -rf", VAR_LIB_GGQ))).get();
            } else if (testArguments.generateConfig) {
                log.info("Generating the {} config", VAR_LIB_GGQ);
                // Copy the <AWS Account #>.<Region>.CoreAndGroupInfo.json to /var/lib/GGQ on the device
                Try.of(() -> ioHelper.runCommand(finalSession, String.join(" ", "sudo mkdir -p", VAR_LIB_GGQ))).get();

                Try.of(() -> ioHelper.runCommand(finalSession, String.join(" ", "sudo chmod 777", VAR_LIB_GGQ))).get();

                File coreAndGroupInfoJsonTemp = Try.of(() -> ioHelper.getTempFile("CoreAndGroupInfoJson", "tmp")).get();
                coreAndGroupInfoJsonTemp.deleteOnExit();

                String coreAndGroupInfoJson = generateCoreAndGroupInfoJson(groupInformation);
                ioHelper.writeFile(coreAndGroupInfoJsonTemp, coreAndGroupInfoJson.getBytes());

                String remoteCoreAndGroupInfoFilename = String.join("/",
                        VAR_LIB_GGQ,
                        String.join(".",
                                iamHelper.getAccountId(),
                                awsHelper.getCurrentRegion().toString(),
                                "CoreAndGroupInfo",
                                "json"));

                Try.run(() -> ioHelper.sendFile(finalSession, coreAndGroupInfoJsonTemp.getAbsolutePath(), remoteCoreAndGroupInfoFilename)).get();
            } else {
                log.info("Not cleaning or generating the config in {}", VAR_LIB_GGQ);
            }

            // Stop Greengrass if it is running already
            log.info("Stopping Greengrass if it is running");
            Try.of(() -> ioHelper.runCommand(finalSession, "sudo systemctl stop greengrass")).get();
            Try.of(() -> ioHelper.runCommand(finalSession, "/greengrass/ggc/core/greengrassd stop")).get();

            // Create the config.json for the local configs directory
            String localConfigJson = createLocalConfigJson();

            // Create the device.json for the local configs directory
            String localDeviceJson = createLocalDeviceJson(testArguments.deviceUnderTest, testArguments.user, testArguments.privateKeyPath, testArguments.architecture);

            List<File> topLevelFiles = List.of(deviceTesterDirectory.listFiles());

            if (topLevelFiles.size() != 1) {
                throw new RuntimeException("Extracted more files than expected, could not find configs directory");
            }

            deviceTesterDirectory = topLevelFiles.single();

            Path configsPath = deviceTesterDirectory.toPath().resolve("configs");

            if (!configsPath.toFile().exists()) {
                throw new RuntimeException("Could not find configs directory");
            }

            // Copy config.json to the local configs directory
            ioHelper.writeFile(configsPath.resolve("config.json").toAbsolutePath().toString(), localConfigJson.getBytes());

            // Copy device.json to the local configs directory
            ioHelper.writeFile(configsPath.resolve("device.json").toAbsolutePath().toString(), localDeviceJson.getBytes());

            Path deviceTesterPath = deviceTesterDirectory.toPath();

            // Look for all of the binaries in subdirectories and make them executable
            Try.of(() -> java.nio.file.Files.walk(deviceTesterPath.resolve("tests"))).get()
                    .map(path -> path.toAbsolutePath().toString())
                    .filter(path -> path.matches(".*/bin/[^/]+$"))
                    .forEach(ioHelper::makeExecutable);

            // Execute some cleanup commands to prevent test failures

            // Prevent "File exists" error on ipd_test_1 and ipd_test_2 if the test ran previously
            Try.of(() -> ioHelper.runCommand(finalSession, "sudo ip address del 172.0.0.2/32 dev lo")).get();

            // Find the binary and execute it
            List<File> mainExecutables = List.of(deviceTesterDirectory.toPath().resolve("bin").toFile().listFiles());

            if (mainExecutables.size() != 1) {
                throw new RuntimeException("Could not locate the Device Tester binary");
            }

            File mainExecutable = mainExecutables.single();
            ioHelper.makeExecutable(mainExecutable.getAbsolutePath());

            File executionDirectory = mainExecutable.getParentFile().getParentFile();

            List<String> deviceTesterAndArguments = List.of(
                    "./bin/" + mainExecutable.getName(),
                    "run-suite",
                    "--suite-id",
                    "GGQ_1",
                    "--pool-id",
                    DEVICE_POOL_ID);

            ProcessBuilder deviceTesterProcessBuilder = processHelper.getProcessBuilder(deviceTesterAndArguments.asJava())
                    .directory(executionDirectory);

            Instant testStart = Instant.now();
            java.util.HashMap<String, Try> testStatus = new java.util.HashMap<>();
            java.util.List<String> reportLocations = new ArrayList<>();

            // Kill any existing proxies left over from previous runs
            killRemoteProcessesBySearchString(finalSession, PROXY_SEARCH_STRING);

            // Kill any existing daemons left over from previous runs
            killRemoteProcessesBySearchString(finalSession, DAEMON_SEARCH_STRING);

            // Kill any existing tail commands we may have started in previous runs
            killRemoteProcessesBySearchString(finalSession, TAIL_FOLLOW_COMMAND);

            // Remove the existing runtime.log
            Try.of(() -> ioHelper.runCommand(finalSession, String.join(" ", "sudo rm -f", FULL_RUNTIME_LOG_PATH))).get();

            // Start device tester
            Optional<Integer> exitVal = processHelper.getOutputFromProcess(log, deviceTesterProcessBuilder, true,
                    Optional.of(stdoutLogMessage -> handleLogMessage(stdoutLogMessage, testStatus, reportLocations)),
                    Optional.of(stderrLogMessage -> handleLogMessage(stderrLogMessage, testStatus, reportLocations)));

            Instant testEnd = Instant.now();

            Duration testDuration = Duration.between(testStart, testEnd);

            log.info("Test duration: [{}]", testDuration);

            exitVal.ifPresent(this::logIfDeviceTesterExitedWithError);

            java.util.List<String> testNames = testStatus.entrySet()
                    .stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            java.util.List<String> passingTests = testStatus.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().isSuccess())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            java.util.List<String> failingTests = testStatus.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().isFailure())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (testNames.size() == 0) {
                log.error("No tests executed");
            } else {
                log.info("Tests executed: ");

                testNames.stream().forEach(log::info);
            }

            if (passingTests.size() == 0) {
                log.error("No tests passed");
            } else {
                log.info("Tests passed: ");

                passingTests.forEach(log::info);
            }

            if (failingTests.size() == 0) {
                log.info("No tests failed");
            } else {
                log.warn("Tests failed: ");

                failingTests.forEach(log::warn);
            }

            // Move the results to the requested location
            String groupName = testArguments.groupName;
            String outputDirectory = String.join("/", testArguments.outputDirectory,
                    String.join("-", groupName, testStartLocalDateTime.toString()));

            reportLocations.stream().findFirst().ifPresent(path ->
                    Try.run(() -> moveParentDirectory(path, outputDirectory))
                            .onFailure(Throwable::printStackTrace)
                            .get());
        } finally {
            safeDisconnect(session);
        }
    }

    private void moveParentDirectory(String path, String outputDirectory) throws IOException {
        new File(outputDirectory).mkdirs();

        File parentFile = new File(path).getParentFile();
        File resultsDirectory = new File(String.join("/", outputDirectory, "results"));

        log.info("Moving results from [{}] to [{}]", parentFile, resultsDirectory);

        if (ioHelper.isRunningInDocker()) {
            log.warn("Since IDT is running in Docker these files should be copied to the host system as well");
        }

        FileUtils.copyDirectory(parentFile, resultsDirectory);
    }

    private void killRemoteProcessesBySearchString(Session finalSession, String searchString) {
        Try.of(() -> ioHelper.runCommand(finalSession, "ps ax | grep '" + searchString + "' | awk '{ print $1 }' | xargs sudo kill -9")).get();
    }

    private void safeDisconnect(Session session) {
        // Don't call get here, we don't care if it fails
        Try.run(session::disconnect);
    }

    private File extractDeviceTester(File deviceTesterZip) throws IOException {
        File deviceTesterDirectory = Files.createTempDirectory(ioHelper.getUuid()).toFile();
        deviceTesterDirectory.deleteOnExit();
        log.info("Extracting Device Tester to [{}] ...", deviceTesterDirectory.getAbsolutePath());
        Path deviceTesterPath = deviceTesterDirectory.toPath();
        Try.run(() -> ioHelper.extractZip(deviceTesterZip, deviceTesterPath, filename -> filename)).get();
        return deviceTesterDirectory;
    }

    private void logIfDeviceTesterExitedWithError(Integer value) {
        if (value != 0) {
            log.error("Device tester exited with an error");
        }
    }

    private void handleLogMessage(String logMessage,
                                  java.util.HashMap<String, Try> testStatus,
                                  java.util.List<String> reportLocations) {
        deviceTesterHelper.log(logMessage);

        DeviceTesterLogMessageType logMessageType = deviceTesterHelper.getLogMessageType(logMessage);

        // Failure to add a remote file resource can indicate that there was a failure to sudo inside of device tester
        if (logMessageType.equals(DeviceTesterLogMessageType.FAIL_TO_ADD_REMOTE_FILE_RESOURCE)) {
            log.warn("The user running Device Tester on the remote host may not have permissions to sudo without a password.");
            log.warn("To use GGP with Device Tester the user will need to be able to perform a passwordless sudo.");
            log.warn("To enable passwordless sudo see this article: https://serverfault.com/a/160587");
            log.warn("");
            log.warn("If Device Tester fails after this point please enable passwordless sudo for the user and try again.");
        }

        io.vavr.collection.Map<String, String> values = deviceTesterHelper.extractValuesFromLogMessage(logMessage);

        Option<String> optionalTestCaseId = deviceTesterHelper.getOptionalTestCaseId(values);

        if (logMessageType.equals(DeviceTesterLogMessageType.RUNNING)) {
            // A test started

            // Clear the test name
            optionalCurrentRunningTest = Option.none();
        }

        if (optionalTestCaseId.isDefined()) {
            // Use the test name from one of these specific message types since the test name is not always consistent across log messages
            if (optionalCurrentRunningTest.isEmpty()) {
                // If there's no existing name then use the current one
                optionalCurrentRunningTest = optionalTestCaseId;
            } else {
                String currentRunningTest = optionalCurrentRunningTest.get();
                String testCaseId = optionalTestCaseId.get();

                if (testCaseId.length() > currentRunningTest.length()) {
                    // Use the longest (most precise) test name available
                    optionalCurrentRunningTest = optionalTestCaseId;
                }
            }
        }

        if (logMessageType.equals(DeviceTesterLogMessageType.ALL_TESTS_FINISHED)) {
            String message = deviceTesterHelper.extractValuesFromLogMessage(logMessage).get(DeviceTesterHelper.MESSAGE_FIELD_NAME).get();
            String aggregatedReportLocation = message.substring(DeviceTesterLogMessageType.Constants.ALL_TESTS_FINISHED_MESSAGE.length());
            reportLocations.add(aggregatedReportLocation);
            return;
        }

        if (logMessageType.equals(DeviceTesterLogMessageType.REPORT_GENERATED)) {
            String message = deviceTesterHelper.extractValuesFromLogMessage(logMessage).get(DeviceTesterHelper.MESSAGE_FIELD_NAME).get();
            String reportLocation = message.substring(DeviceTesterLogMessageType.Constants.REPORT_GENERATED_MESSAGE.length());
            reportLocations.add(reportLocation);
            return;
        }

        if (!logMessageType.equals(DeviceTesterLogMessageType.FAIL_WITH_DURATION) &&
                !(logMessageType.equals(DeviceTesterLogMessageType.PASS))) {
            // Not a pass/fail message
            return;
        }

        String currentRunningTest = optionalCurrentRunningTest.get();

        // A test passed or failed

        // Store the related log lines in the test log index
        /*
        getTestLogs(currentRunningTest, logs, testLogIndex);
        */

        // Store the pass/fail status
        if (logMessageType.equals(DeviceTesterLogMessageType.FAIL_WITH_DURATION)) {
            testStatus.put(currentRunningTest, Try.failure(new RuntimeException("Test failed")));
        } else {
            testStatus.put(currentRunningTest, Try.success(null));
        }
    }

    /*
    private void getTestLogs(String testCaseId, java.util.List<String> logs, java.util.List<Tuple3<String, Integer, Integer>> testLogIndex) {
        int previousIndex = 0;

        if (testLogIndex.size() > 0) {
            previousIndex = testLogIndex.get(testLogIndex.size() - 1)._3;
        }

        Tuple3 testCaseStartEnd = new Tuple3<>(testCaseId, previousIndex, logs.size());

        testLogIndex.add(testCaseStartEnd);
    }
    */

    private String createLocalDeviceJson(String deviceUnderTest, String user, String privateKeyPath, Architecture architecture) {
        Map<String, String>[] features = new Map[2];

        features[0] = HashMap.of("name", "arch")
                .put("value", architectureToValue(architecture))
                .toJavaMap();
        features[1] = HashMap.of("name", "os")
                .put("value", "linux")
                .toJavaMap();

        Map<String, String> credentials = HashMap.of("user", user)
                .put("privKeyPath", privateKeyPath)
                .toJavaMap();

        Map<String, Object> auth = HashMap.of("credentials", (Object) credentials)
                .put("method", "pki")
                .toJavaMap();

        Map<String, Object> connectivity = HashMap.of("auth", (Object) auth)
                .put("protocol", "ssh")
                .put("ip", deviceUnderTest)
                .toJavaMap();

        Map<String, Object>[] devices = new Map[1];
        devices[0] = HashMap.of("connectivity", (Object) connectivity)
                .put("id", "DUT")
                .toJavaMap();

        Map<String, Object>[] outerMap = new Map[1];
        outerMap[0] = HashMap.of("features", (Object) features)
                .put("id", DEVICE_POOL_ID)
                .put("greengrassLocation", "/greengrass")
                .put("devices", devices)
                .put("sku", "SKU")
                .toJavaMap();

        return jsonHelper.toJson(outerMap);
    }

    private String architectureToValue(Architecture architecture) {
        if (Architecture.ARMV7L_OPENWRT.equals(architecture) ||
                Architecture.ARMV7L_RASPBIAN.equals(architecture)) {
            return "armv7l";
        }

        if (Architecture.ARMV6L_RASPBIAN.equals(architecture)) {
            return "armv6l";
        }

        if (Architecture.ARMV8.equals(architecture) ||
                Architecture.ARMV8_OPENWRT.equals(architecture)) {
            return "aarch64";
        }

        if (Architecture.X86_64.equals(architecture)) {
            return "x86_64";
        }

        throw new RuntimeException("Unsupported architecture");
    }

    private String generateCoreAndGroupInfoJson(GroupInformation groupInformation) {
        String groupId = greengrassHelper.getGroupId(groupInformation);
        String coreThingName = ggVariables.getCoreThingName(groupInformation.name());

        Map<String, String> coreInfo = HashMap.of("ThingArn", iotHelper.getThingArn(coreThingName))
                .put("ThingName", coreThingName)
                .put("CertArn", iotHelper.getThingPrincipal(coreThingName))
                .put("IotEndpoint", iotHelper.getEndpoint())
                .toJavaMap();

        Map<String, Object> outerMap = HashMap.of("CoreInfo", (Object) coreInfo)
                .put("GroupId", groupId)
                .toJavaMap();

        return jsonHelper.toJson(outerMap);
    }

    private String createLocalConfigJson() {
        Map<String, String> log = HashMap.of("location", "../logs/").toJavaMap();

        Map<String, String> configFiles = HashMap.of("root", "../configs")
                .put("device", "../configs/device.json")
                .toJavaMap();

        Map<String, String> credentials = HashMap.of("profile", "default").toJavaMap();

        Map<String, Object> auth = HashMap.of("method", (Object) "file")
                .put("credentials", credentials)
                .toJavaMap();

        Map<String, Object> outerMap = HashMap.of("log", (Object) log)
                .put("configFiles", configFiles)
                .put("testPath", "../tests/")
                .put("reportPath", "../results/")
                .put("certificatePath", "../certificates/")
                .put("awsRegion", awsHelper.getCurrentRegion().toString())
                .put("auth", auth)
                .toJavaMap();

        return jsonHelper.toJson(outerMap);
    }

    @Override
    public ArgumentHelper<TestArguments> getArgumentHelper() {
        return testArgumentHelper;
    }

    @Override
    public TestArguments getArguments() {
        return new TestArguments();
    }
}
