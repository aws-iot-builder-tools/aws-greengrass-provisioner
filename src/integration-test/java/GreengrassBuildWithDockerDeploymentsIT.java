import com.awslabs.aws.greengrass.provisioner.implementations.clientproviders.AwsCredentialsProvider;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicGlobalDefaultHelper;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicProcessHelper;
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
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GreengrassBuildWithDockerDeploymentsIT {
    private static Logger log = LoggerFactory.getLogger(GreengrassBuildWithDockerDeploymentsIT.class);
    private static GreengrassITShared greengrassITShared;
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public ExpectedSystemExit expectedSystemExit = ExpectedSystemExit.none();

    @BeforeClass
    public static void beforeClassSetup() throws IOException, InterruptedException {
        greengrassITShared = new GreengrassITShared();
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
        GreengrassITShared.beforeTestSetup();
    }

    @After
    public void afterTestTeardown() throws IOException {
        GreengrassITShared.cleanDirectories();
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
        return String.join(":", GreengrassITShared.TIMMATTISON_AWS_GREENGRASS_PROVISIONER, getBranch());
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
        /* Can be used when debugging
        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
        genericContainer.followOutput(logConsumer);
         */

        GreengrassITShared.getThreadHelper().timeLimitTask(() -> {
                    while (genericContainer.isRunning()) {
                        Thread.sleep(1000);
                        /* Can be used when debugging
                        log.info("Waiting for container to finish...");
                         */
                    }
                    return Optional.empty();
                },
                2, TimeUnit.MINUTES);
    }

    // Test set 1: Expected failure with invalid deployment name in Docker
    @Test
    public void shouldFailWithDocker() {
        runContainer(greengrassITShared.FAIL_DEPLOYMENT_COMMAND, not(equalTo(0)));
    }

    // Test set 2: Expected success with CDD skeleton with Docker
    @Test
    public void shouldBuildJavaFunctionWithDocker() {
        runContainer(greengrassITShared.CDD_SKELETON_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 3: Expected success with Python Hello World with Docker
    @Test
    public void shouldBuildPythonFunctionWithDocker() {
        runContainer(greengrassITShared.PYTHON_HELLO_WORLD_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 4: Expected success with Python LiFX function (has dependencies to fetch) with Docker
    @Test
    public void shouldBuildPythonFunctionWithDependenciesWithDocker() {
        runContainer(greengrassITShared.LIFX_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 5: Expected success with Node Hello World with Docker
    @Test
    public void shouldBuildNodeFunctionWithDocker() {
        runContainer(greengrassITShared.NODE_HELLO_WORLD_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 6: Expected success with all three languages in one build with Docker
    @Test
    public void shouldBuildCombinedFunctionWithDocker() {
        runContainer(greengrassITShared.ALL_HELLO_WORLD_DEPLOYMENT_COMMAND, equalTo(0));
    }

    // Test set 7: Expected failure to launch an EC2 instance with ARM32 with Docker
    @Test
    public void shouldFailEc2LaunchWithArm32WithDocker() {
        runContainer(greengrassITShared.EC2_ARM32_NODE_HELLO_WORLD_DEPLOYMENT_COMMAND, not(equalTo(0)));
    }

    // Test set 8: Expected success with Node Express function (has dependencies to fetch) with Docker
    @Test
    public void shouldBuildNodeFunctionWithDependenciesWithDocker() {
        runContainer(greengrassITShared.NODE_WEBSERVER_DEPLOYMENT_COMMAND, equalTo(0));
    }

    private void runContainer(String nodeHelloWorldDeploymentCommand, Matcher<Integer> integerMatcher) {
        GenericContainer genericContainer = startAndGetContainer(nodeHelloWorldDeploymentCommand);
        waitForContainerToFinish(genericContainer);

        System.out.println(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(integerMatcher));
    }
}
