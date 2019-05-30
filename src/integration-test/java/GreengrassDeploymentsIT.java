import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.implementations.clientproviders.AwsCredentialsProvider;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicGlobalDefaultHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicProcessHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicThreadHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.SingleThreadedExecutorHelper;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GreengrassDeploymentsIT {
    private static final File NODEJS_SDK_FROM_BUILD = new File("./build/foundation/aws-greengrass-core-sdk-js.zip");
    private static final File NODEJS_SDK_REQUIRED_FOR_TESTING = new File("foundation/aws-greengrass-core-sdk-js.zip");
    private static final File MASTER_DEPLOYMENTS = new File("../aws-greengrass-lambda-functions/deployments");
    private static final File MASTER_FUNCTIONS = new File("../aws-greengrass-lambda-functions/functions");
    private static final File MASTER_FOUNDATION = new File("../aws-greengrass-lambda-functions/foundation");
    private static final String TIMMATTISON_AWS_GREENGRASS_PROVISIONER = "timmattison/aws-greengrass-provisioner";
    private static final File TEMP_DEPLOYMENTS = new File("deployments");
    private static final File TEMP_FUNCTIONS = new File("functions");
    private static final File TEMP_FOUNDATION = new File("foundation");
    private static final File TEMP_CREDENTIALS = new File("credentials");
    private static final String DEPLOYMENT_OPTION = "-d deployments/";
    private static final String ARM32_OPTION = "-a ARM32";
    private static final String EC2_LAUNCH_OPTION = "--ec2-launch";
    private static final String CDD_SKELETON_DEPLOYMENT = "cdd-skeleton.conf";
    private static final String PYTHON_HELLO_WORLD_DEPLOYMENT = "python-hello-world.conf";
    private static final String LIFX_DEPLOYMENT = "lifx.conf";
    private static final String NODE_WEBSERVER_DEPLOYMENT = "web-server-node.conf";
    private static final String NODE_HELLO_WORLD_DEPLOYMENT = "node-hello-world.conf";
    private static final String ALL_HELLO_WORLD_DEPLOYMENT = "all-hello-world.conf";
    private static final String FAIL_DEPLOYMENT = "FAKE";
    private static final String GROUP_OPTION = String.join(" ", "-g", UUID.randomUUID().toString());
    private static final String CDD_SKELETON_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, CDD_SKELETON_DEPLOYMENT, " ", GROUP_OPTION);
    private static final String FAIL_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, FAIL_DEPLOYMENT, " ", GROUP_OPTION);
    private static final String PYTHON_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, PYTHON_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION);
    private static final String LIFX_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, LIFX_DEPLOYMENT, " ", GROUP_OPTION);
    private static final String NODE_WEBSERVER_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, NODE_WEBSERVER_DEPLOYMENT, " ", GROUP_OPTION);
    private static final String NODE_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, NODE_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION);
    private static final String ALL_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, ALL_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION);
    private static final String EC2_ARM32_NODE_HELLO_WORLD_DEPLOYMENT_COMMAND = String.join("", DEPLOYMENT_OPTION, NODE_HELLO_WORLD_DEPLOYMENT, " ", GROUP_OPTION, " ", ARM32_OPTION, " ", EC2_LAUNCH_OPTION);
    private static Logger log = LoggerFactory.getLogger(GreengrassDeploymentsIT.class);
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public ExpectedSystemExit expectedSystemExit = ExpectedSystemExit.none();

    private BasicThreadHelper basicThreadHelper;

    @BeforeClass
    public static void beforeClassSetup() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", "./build.sh");

        Process process = processBuilder.start();

        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        Thread stdoutThread = new Thread(() -> stdout.lines().forEach(log::info));
        stdoutThread.start();

        Thread stderrThread = new Thread(() -> stderr.lines().forEach(log::warn));
        stderrThread.start();

        process.waitFor();
        processBuilder.environment();
    }

    @Before
    public void beforeTestSetup() throws IOException {
        SingleThreadedExecutorHelper singleThreadedExecutorHelper = new SingleThreadedExecutorHelper();
        basicThreadHelper = new BasicThreadHelper();
        basicThreadHelper.executorHelper = singleThreadedExecutorHelper;

        cleanDirectories();

        FileUtils.copyDirectory(MASTER_DEPLOYMENTS, TEMP_DEPLOYMENTS);
        FileUtils.copyDirectory(MASTER_FUNCTIONS, TEMP_FUNCTIONS);
        FileUtils.copyDirectory(MASTER_FOUNDATION, TEMP_FOUNDATION);
        FileUtils.copyFile(NODEJS_SDK_FROM_BUILD, NODEJS_SDK_REQUIRED_FOR_TESTING);
    }

    @After
    public void afterTestTeardown() throws IOException {
        cleanDirectories();
    }

    private void cleanDirectories() throws IOException {
        FileUtils.deleteDirectory(TEMP_DEPLOYMENTS);
        FileUtils.deleteDirectory(TEMP_FUNCTIONS);
        FileUtils.deleteDirectory(TEMP_FOUNDATION);
        FileUtils.deleteDirectory(TEMP_CREDENTIALS);
    }

    private String getHome() {
        BasicGlobalDefaultHelper basicGlobalDefaultHelper = new BasicGlobalDefaultHelper();
        return basicGlobalDefaultHelper.getHomeDirectory().get();
    }

    private String getBranch() {
        BasicProcessHelper basicProcessHelper = new BasicProcessHelper();
        basicProcessHelper.awsCredentialsProvider = mock(AwsCredentialsProvider.class);
        doReturn(DefaultCredentialsProvider.create().resolveCredentials()).when(basicProcessHelper.awsCredentialsProvider).get();

        List<String> programAndArguments = new ArrayList<>();
        programAndArguments.add("git");
        programAndArguments.add("symbolic-ref");
        programAndArguments.add("--short");
        programAndArguments.add("HEAD");

        ProcessBuilder processBuilder = basicProcessHelper.getProcessBuilder(programAndArguments);

        StringBuilder stringBuilder = new StringBuilder();

        basicProcessHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stringBuilder::append), Optional.empty());

        String branch = stringBuilder.toString();

        String filteredBranch = branch.replaceAll("[^A-Za-z0-9._-]", "");

        return filteredBranch;
    }

    private String getContainerName() {
        return String.join(":", TIMMATTISON_AWS_GREENGRASS_PROVISIONER, getBranch());
    }

    private GenericContainer startAndGetContainer(String arguments) {
        String hostAwsCredentialsPath = String.join("/", getHome(), ".aws");
        String containerAwsCredentialsPath = "/root/.aws";

        String hostFoundationPath = getHostPath("foundation");
        String containerFoundationPath = "/foundation";

        String hostDeploymentsPath = getHostPath("deployments");
        String containerDeploymentsPath = "/deployments";

        String hostFunctionsPath = getHostPath("functions");
        String containerFunctionsPath = "/functions";

        String hostGgdsPath = getHostPath("ggds");
        String containerGgdsPath = "/ggds";

        String baseCommand = "java -jar AwsGreengrassProvisioner.jar";
        String command = baseCommand.join(" ", arguments);

        GenericContainer genericContainer = new GenericContainer<>(getContainerName())
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostAwsCredentialsPath).toPath()), containerAwsCredentialsPath)
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostFoundationPath).toPath()), containerFoundationPath)
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostDeploymentsPath).toPath()), containerDeploymentsPath)
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostFunctionsPath).toPath()), containerFunctionsPath)
                .withCopyFileToContainer(MountableFile.forHostPath(new File(hostGgdsPath).toPath()), containerGgdsPath)
                .withCommand(command);

        genericContainer.start();

        return genericContainer;
    }

    @NotNull
    private String getHostPath(String foundation) {
        return String.join("/", "..", "aws-greengrass-lambda-functions", foundation);
    }

    private void waitForContainerToFinish(GenericContainer genericContainer) {
//        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
//        genericContainer.followOutput(logConsumer);

        basicThreadHelper.timeLimitTask(() -> {
                    while (genericContainer.isRunning()) {
                        Thread.sleep(1000);
//                        log.info("Waiting for container to finish...");
                    }
                    return Optional.empty();
                },
                2, TimeUnit.MINUTES);
    }

    // Test set 1: Expected failure with invalid deployment name in Docker
    @Test
    public void shouldFailWithDocker() {
        runContainer(FAIL_DEPLOYMENT_COMMAND, not(equalTo(0)));
    }

    // Test set 1: Expected failure with invalid deployment name
    @Test
    public void shouldFailWithoutDocker() {
        expectedSystemExit.expectSystemExitWithStatus(1);
        AwsGreengrassProvisioner.main(split(FAIL_DEPLOYMENT_COMMAND));
    }

    // Test set 2: Expected success with CDD skeleton with Docker
    @Test
    public void shouldBuildJavaFunctionWithDocker() {
        runContainer(CDD_SKELETON_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 2: Expected success with CDD skeleton
    @Test
    public void shouldBuildJavaFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(CDD_SKELETON_DEPLOYMENT_COMMAND));
    }

    // Test set 3: Expected success with Python Hello World with Docker
    @Test
    public void shouldBuildPythonFunctionWithDocker() {
        runContainer(PYTHON_HELLO_WORLD_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 3: Expected success with Python Hello World
    @Test
    public void shouldBuildPythonFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(PYTHON_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 4: Expected success with Python LiFX function (has dependencies to fetch) with Docker
    @Test
    public void shouldBuildPythonFunctionWithDependenciesWithDocker() {
        runContainer(LIFX_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 4: Expected success with Python LiFX function (has dependencies to fetch)
    @Test
    public void shouldBuildPythonFunctionWithDependenciesWithoutDocker() {
        AwsGreengrassProvisioner.main(split(LIFX_DEPLOYMENT_COMMAND));
    }

    // Test set 5: Expected success with Node Hello World with Docker
    @Test
    public void shouldBuildNodeFunctionWithDocker() {
        runContainer(NODE_HELLO_WORLD_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 5: Expected success with Node Hello World
    @Test
    public void shouldBuildNodeFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(NODE_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 6: Expected success with all three languages in one build with Docker
    @Test
    public void shouldBuildCombinedFunctionWithDocker() {
        runContainer(ALL_HELLO_WORLD_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 6: Expected success with all three languages in one build
    @Test
    public void shouldBuildCombinedFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(ALL_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 7: Expected failure to launch an EC2 instance with ARM32 with Docker
    @Test
    public void shouldFailEc2LaunchWithArm32WithDocker() {
        runContainer(EC2_ARM32_NODE_HELLO_WORLD_DEPLOYMENT_COMMAND, not(equalTo(0)));
    }

    // Test set 7: Expected failure to launch an EC2 instance with ARM32 with Docker
    @Test
    public void shouldFailEc2LaunchWithArm32WithoutDocker() {
        expectedSystemExit.expectSystemExitWithStatus(1);
        AwsGreengrassProvisioner.main(split(EC2_ARM32_NODE_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 8: Expected success with Node Express function (has dependencies to fetch) with Docker
    @Test
    public void shouldBuildNodeFunctionWithDependenciesWithDocker() {
        runContainer(NODE_WEBSERVER_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 8: Expected success with Node Express function (has dependencies to fetch)
    @Test
    public void shouldBuildNodeFunctionWithDependenciesWithoutDocker() {
        AwsGreengrassProvisioner.main(split(NODE_WEBSERVER_DEPLOYMENT_COMMAND));
    }

    private String[] split(String input) {
        return input.split(" ");
    }

    private void runContainer(String nodeHelloWorldDeploymentCommand, Matcher<Integer> integerMatcher) {
        GenericContainer genericContainer = startAndGetContainer(nodeHelloWorldDeploymentCommand);
        waitForContainerToFinish(genericContainer);

        System.out.println(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(integerMatcher));
    }
}
