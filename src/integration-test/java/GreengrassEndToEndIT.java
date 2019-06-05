import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicGGConstants;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicGGVariables;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicIoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.iot.websockets.BasicMqttOverWebsocketsProvider;
import com.google.gson.Gson;
import org.awaitility.Duration;
import org.eclipse.paho.client.mqttv3.*;
import org.hamcrest.collection.IsMapContaining;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

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
    private Gson gson;
    private boolean flag;
    private Duration defaultTimeout = new Duration(90, TimeUnit.SECONDS);

    @Before
    public void beforeTestSetup() throws IOException, NoSuchAlgorithmException, InvalidKeyException, MqttException {
        GreengrassITShared.beforeTestSetup();
        BasicGGConstants basicGGConstants = new BasicGGConstants();
        BasicGGVariables basicGGVariables = new BasicGGVariables();
        basicGGVariables.ggConstants = basicGGConstants;
        String groupName = GreengrassITShared.GROUP_NAME;
        oemArchiveName = new File(basicGGVariables.getOemArchiveName(groupName));
        coreName = GreengrassITShared.GROUP_NAME + "_Core";

        ioHelper = new BasicIoHelper();

        greengrassBuildWithoutDockerDeploymentsIT = new GreengrassBuildWithoutDockerDeploymentsIT();

        BasicMqttOverWebsocketsProvider basicMqttOverWebsocketsProvider = new BasicMqttOverWebsocketsProvider();
        String clientId = UUID.randomUUID().toString();

        mqttClient = basicMqttOverWebsocketsProvider.getMqttClient(clientId);
        mqttClient.connect();

        gson = new Gson();

        flag = false;

        defaultTimeout = new Duration(90, TimeUnit.SECONDS);
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
    public void shouldStartWithNodeConfiguration() throws IOException, InterruptedException, MqttException {
        greengrassBuildWithoutDockerDeploymentsIT.shouldBuildNodeFunctionWithoutDocker();

        String expectedTopic = String.join("/", coreName, "node", "hello", "world");

        setupHelloWorldFunctionCheck(expectedTopic);

        waitForContainerToStartSuccessfully(defaultTimeout);
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
                Map<String, Object> payloadMap = gson.fromJson(payloadString, Map.class);
                Assert.assertThat(payloadMap, IsMapContaining.hasKey("message"));
                setFlag(true);
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

        await().atMost(waitDuration).until(optionalBooleanCallable.orElse(this::getFlag));
    }

    private boolean getFlag() {
        return flag;
    }

    private void setFlag(boolean value) {
        this.flag = value;
    }
}
