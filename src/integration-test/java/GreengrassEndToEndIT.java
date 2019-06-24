import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicGGConstants;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicGGVariables;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicIoHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicJsonHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import com.awslabs.aws.iot.websockets.BasicMqttOverWebsocketsProvider;
import org.awaitility.Duration;
import org.eclipse.paho.client.mqttv3.*;
import org.hamcrest.collection.IsMapContaining;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;

public class GreengrassEndToEndIT {
    private static final String GREENGRASS_DIRECTORY = "/greengrass";
    private static final String GREENGRASS_OEM_TAR = GREENGRASS_DIRECTORY + "/oem.tar";
    private static final String STARTUP_SH = "/startup.sh";
    private static final String GREENGRASS_ENTRYPOINT_SH = "./greengrass-entrypoint.sh";
    // TODO: Temporarily using Docker Hub, should migrate this to ECR to get guaranteed official images
    private static final String AMAZON_AWS_IOT_GREENGRASS_1_9_1_AMAZON_LINUX = "amazon/aws-iot-greengrass:1.9.1-amazonlinux";
    /**
     * Makes regex DOT character match newlines
     */
    private static final String REGEX_DOT_ALL = "(?s)";
    // private static final String GREENGRASS_DIED_ERROR_MESSAGE = String.join("", REGEX_DOT_ALL, ".*The Greengrass daemon process .* died.*");
    private static final String GREENGRASS_STARTED_MESSAGE = String.join("", REGEX_DOT_ALL, ".*Greengrass successfully started with PID:.*");
    private static Logger log = LoggerFactory.getLogger(GreengrassEndToEndIT.class);
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public ExpectedSystemExit expectedSystemExit = ExpectedSystemExit.none();

    private File oemArchiveName;
    private IoHelper ioHelper;
    private GreengrassBuildWithoutDockerDeploymentsIT greengrassBuildWithoutDockerDeploymentsIT;
    private MqttClient mqttClient;
    private String coreName;
    private AtomicBoolean flag;
    private Duration defaultTimeout = new Duration(90, TimeUnit.SECONDS);
    private Path functionDefaultsPath;
    private GreengrassITShared greengrassITShared;
    private JsonHelper jsonHelper;

    @Before
    public void beforeTestSetup() throws IOException, NoSuchAlgorithmException, InvalidKeyException, MqttException {
        GreengrassITShared.beforeTestSetup();
        greengrassITShared = new GreengrassITShared();

        // Since we're testing with Docker but not using the native Docker launch feature we need to set the default function isolation mode to no container
        functionDefaultsPath = new File("deployments/function.defaults.conf").toPath();

        replaceStringInFunctionDefaults("greengrassContainer\\s*=\\strue", "greengrassContainer = false");

        GGVariables ggVariables = AwsGreengrassProvisioner.getInjector().getInstance(GGVariables.class);
        String groupName = greengrassITShared.GROUP_NAME;
        oemArchiveName = new File(ggVariables.getOemArchiveName(groupName));
        coreName = greengrassITShared.GROUP_NAME + "_Core";

        ioHelper = AwsGreengrassProvisioner.getInjector().getInstance(IoHelper.class);

        greengrassBuildWithoutDockerDeploymentsIT = new GreengrassBuildWithoutDockerDeploymentsIT();
        greengrassBuildWithoutDockerDeploymentsIT.greengrassITShared = greengrassITShared;

        BasicMqttOverWebsocketsProvider basicMqttOverWebsocketsProvider = new BasicMqttOverWebsocketsProvider();
        String clientId = ioHelper.getUuid();

        mqttClient = basicMqttOverWebsocketsProvider.getMqttClient(clientId);
        mqttClient.connect();

        jsonHelper = AwsGreengrassProvisioner.getInjector().getInstance(JsonHelper.class);

        flag = new AtomicBoolean(false);

        defaultTimeout = new Duration(90, TimeUnit.SECONDS);
    }

    private void replaceStringInFunctionDefaults(String regex, String replacement) throws IOException {
        try (Stream<String> lines = Files.lines(functionDefaultsPath)) {
            List<String> replaced = lines
                    .map(line -> line.replaceAll(regex, replacement))
                    .collect(Collectors.toList());
            Files.write(functionDefaultsPath, replaced);
        }
    }

    @After
    public void afterTestTeardown() throws IOException {
        GreengrassITShared.cleanDirectories();
    }

    @Test
    public void shouldCreateOemFile() {
        greengrassBuildWithoutDockerDeploymentsIT.shouldBuildNodeFunctionWithoutDocker();

        Assert.assertThat(oemArchiveName.exists(), is(true));
    }

    @Test
    public void shouldFailWhenTryingToRunFunctionWithUidAndGidZero() throws IOException, InterruptedException, MqttException {
        expectedSystemExit.expectSystemExitWithStatus(1);

        // First build with the normal UID and GID configuration
        greengrassBuildWithoutDockerDeploymentsIT.shouldBuildNodeFunctionWithoutDocker();

        setupHelloWorldFunctionCheck(getNodeExpectedTopic());

        waitForContainerToStartSuccessfully(defaultTimeout);

        // Redeploy with the UID and GID set to zero, this should fail because the original group.json wasn't set up to allow functions to run as root
        replaceStringInFunctionDefaults("uid\\s*=\\s[0-9]+", "uid = 0");
        replaceStringInFunctionDefaults("gid\\s*=\\s[0-9]+", "gid = 0");

        greengrassBuildWithoutDockerDeploymentsIT.shouldBuildNodeFunctionWithoutDocker();

        Assert.fail("This should throw an exception");
    }

    @Test
    public void shouldStartWithNodeConfiguration() throws IOException, InterruptedException, MqttException {
        greengrassBuildWithoutDockerDeploymentsIT.shouldBuildNodeFunctionWithoutDocker();

        setupHelloWorldFunctionCheck(getNodeExpectedTopic());

        waitForContainerToStartSuccessfully(defaultTimeout);
    }

    @NotNull
    public String getNodeExpectedTopic() {
        return String.join("/", coreName, "node", "hello", "world");
    }

    @Test
    public void shouldStartWithJavaConfiguration() throws IOException, InterruptedException, MqttException {
        greengrassBuildWithoutDockerDeploymentsIT.shouldBuildJavaFunctionWithoutDocker();

        String expectedTopic = String.join("/", coreName, "cdd", "skeleton", "output");

        setupHelloWorldFunctionCheck(expectedTopic);

        waitForContainerToStartSuccessfully(defaultTimeout);
    }

    private void setupHelloWorldFunctionCheck(String expectedTopic) throws MqttException {
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                Assert.assertThat(topic, is(expectedTopic));
                String payloadString = new String(message.getPayload());
                Map<String, Object> payloadMap = jsonHelper.fromJson(Map.class, payloadString.getBytes());
                Assert.assertThat(payloadMap, IsMapContaining.hasKey("message"));
                flag.set(true);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        mqttClient.subscribe(expectedTopic);
    }

    /**
     * Waits for a container to start, and then waits for the flag value to toggle to true for the specified wait duration
     *
     * @param waitDuration
     * @throws IOException
     * @throws InterruptedException
     */
    private void waitForContainerToStartSuccessfully(Duration waitDuration) throws IOException, InterruptedException {
        waitForContainerToStartSuccessfully(Optional.of(waitDuration), Optional.empty());
    }

    private void waitForContainerToStartSuccessfully(Optional<Duration> optionalWaitDuration, Optional<Callable<Boolean>> optionalBooleanCallable) throws IOException, InterruptedException {
        String script = String.join("\n",
                "#!/usr/bin/env bash",
                "tar xvf " + GREENGRASS_OEM_TAR + " -C " + GREENGRASS_DIRECTORY,
                GREENGRASS_ENTRYPOINT_SH);

        File tempScript = File.createTempFile("script-", ".sh");
        tempScript.deleteOnExit();
        ioHelper.writeFile(tempScript, script.getBytes());
        tempScript.setExecutable(true);

        String command = "bash " + STARTUP_SH;

        GenericContainer greengrass = new GenericContainer(AMAZON_AWS_IOT_GREENGRASS_1_9_1_AMAZON_LINUX)
                .withCopyFileToContainer(MountableFile.forHostPath(tempScript.toPath()), STARTUP_SH)
                .withCopyFileToContainer(MountableFile.forHostPath(oemArchiveName.toPath()), GREENGRASS_OEM_TAR)
                .withExposedPorts(8883, 8000)
                .withCommand(command)
                .waitingFor(Wait.forLogMessage(GREENGRASS_STARTED_MESSAGE, 1));

        greengrass.start();

        log.info("Container started");

        if (!optionalWaitDuration.isPresent()) {
            return;
        }

        Duration waitDuration = optionalWaitDuration.get();
        log.info("Waiting up to " + waitDuration.toString() + " for the configuration to pass secondary tests");

        await().atMost(waitDuration).until(optionalBooleanCallable.orElse(flag::get));
    }
}
