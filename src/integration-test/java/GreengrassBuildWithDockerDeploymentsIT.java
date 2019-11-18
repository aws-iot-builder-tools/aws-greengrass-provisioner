import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GlobalDefaultHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;

@Ignore
public class GreengrassBuildWithDockerDeploymentsIT {
    private static final Matcher<Integer> EXIT_CODE_IS_ZERO = equalTo(0);
    private static final Matcher<Integer> EXIT_CODE_IS_NOT_ZERO = not(EXIT_CODE_IS_ZERO);
    public static final String AWS_GREENGRASS_LAMBDA_FUNCTIONS_VIA_PARENT_DIRECTORY = "../aws-greengrass-lambda-functions";
    private static Logger log = LoggerFactory.getLogger(GreengrassBuildWithDockerDeploymentsIT.class);
    private static GreengrassITShared greengrassITShared;
    private static IoHelper ioHelper;
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public ExpectedSystemExit expectedSystemExit = ExpectedSystemExit.none();

    @BeforeClass
    public static void beforeClassSetup() throws IOException, InterruptedException {
        greengrassITShared = new GreengrassITShared();
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", "./build-ggp-docker-container.sh");

        Process process = processBuilder.start();

        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        Thread stdoutThread = new Thread(() -> stdout.lines().forEach(log::info));
        stdoutThread.start();

        Thread stderrThread = new Thread(() -> stderr.lines().forEach(log::warn));
        stderrThread.start();

        // This builds the Docker container for integration tests. It can take a few minutes if this is a fresh build.
        process.waitFor();

        ioHelper = AwsGreengrassProvisioner.getInjector().getInstance(IoHelper.class);
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
        return AwsGreengrassProvisioner.getInjector().getInstance(GlobalDefaultHelper.class).getHomeDirectory().get();
    }

    private String getBranch() {
        ProcessHelper processHelper = AwsGreengrassProvisioner.getInjector().getInstance(ProcessHelper.class);

        List<String> programAndArguments = new ArrayList<>();
        programAndArguments.add("git");
        programAndArguments.add("symbolic-ref");
        programAndArguments.add("--short");
        programAndArguments.add("HEAD");

        ProcessBuilder processBuilder = processHelper.getProcessBuilder(programAndArguments);

        StringBuilder stringBuilder = new StringBuilder();

        processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stringBuilder::append), Optional.empty());

        String branch = stringBuilder.toString();

        String filteredBranch = branch.replaceAll("[^A-Za-z0-9._-]", "");

        return filteredBranch;
    }

    private String getContainerName() {
        return String.join(":", GreengrassITShared.TIMMATTISON_AWS_GREENGRASS_PROVISIONER, getBranch());
    }

    private GenericContainer startAndGetContainer(String arguments) {
        String hostAwsCredentialsPath = String.join("/", getHome(), ".aws");
        MountableFile hostAwsCredentialsMountableFile = MountableFile.forHostPath(new File(hostAwsCredentialsPath).toPath());
        String containerAwsCredentialsPath = "/root/.aws";

        String hostFoundationPath = getHostPath("foundation");
        MountableFile hostFoundationMountableFile = MountableFile.forHostPath(new File(hostFoundationPath).toPath());
        String containerFoundationPath = "/foundation";

        String hostDeploymentsPath = getHostPath("deployments");
        MountableFile hostDeploymentsMountableFile = MountableFile.forHostPath(new File(hostDeploymentsPath).toPath());
        String containerDeploymentsPath = "/deployments";

        String hostFunctionsPath = getHostPath("functions");
        MountableFile hostFunctionsMountableFile = MountableFile.forHostPath(new File(hostFunctionsPath).toPath());
        String containerFunctionsPath = "/functions";

        String hostGgdsPath = getHostPath("ggds");
        MountableFile hostGgdsMountableFile = MountableFile.forHostPath(new File(hostGgdsPath).toPath());
        String containerGgdsPath = "/ggds";

        GenericContainer genericContainer = new GenericContainer<>(getContainerName())
                .withCopyFileToContainer(hostAwsCredentialsMountableFile, containerAwsCredentialsPath)
                .withCopyFileToContainer(hostFoundationMountableFile, containerFoundationPath)
                .withCopyFileToContainer(hostDeploymentsMountableFile, containerDeploymentsPath)
                .withCopyFileToContainer(hostFunctionsMountableFile, containerFunctionsPath)
                .withCopyFileToContainer(hostGgdsMountableFile, containerGgdsPath)
                .withCommand(arguments);

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
        // Run the container and make sure the exit code is NOT 0
        runContainer(greengrassITShared.getFailDeploymentCommand(Optional.empty()), EXIT_CODE_IS_NOT_ZERO);
    }

    // Test set 2: Expected success with CDD skeleton with Docker
    @Test
    public void shouldBuildJavaFunctionWithDocker() {
        runContainer(greengrassITShared.getCddSkeletonDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);
    }

    // Test set 3: Expected success with Python 2 Hello World with Docker
    @Test
    public void shouldBuildPython2FunctionWithDocker() {
        runContainer(greengrassITShared.getPython2HelloWorldDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);
    }

    // Test set 4: Expected success with Python LiFX function (has dependencies to fetch) with Docker
    @Test
    public void shouldBuildPythonFunctionWithDependenciesWithDocker() {
        runContainer(greengrassITShared.getLifxDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);
    }

    // Test set 5: Expected success with Node Hello World with Docker
    @Test
    public void shouldBuildNodeFunctionWithDocker() {
        runContainer(greengrassITShared.getNodeHelloWorldDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);
    }

    // Test set 6: Expected success with all three languages in one build with Docker
    @Test
    public void shouldBuildCombinedFunctionWithDocker() {
        runContainer(greengrassITShared.getAllHelloWorldDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);
    }

    // Test set 7: Expected failure to launch an EC2 instance with ARM32 with Docker
    @Test
    public void shouldFailEc2LaunchWithArm32WithDocker() {
        runContainer(greengrassITShared.getEc2Arm32NodeHelloWorldDeploymentCommand(Optional.empty()), EXIT_CODE_IS_NOT_ZERO);
    }

    // Test set 8: Expected success with Node Express function (has dependencies to fetch) with Docker
    @Test
    public void shouldBuildNodeFunctionWithDependenciesWithDocker() {
        runContainer(greengrassITShared.getNodeWebserverDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);
    }

    // Test set 9: Expected success with Python 3 Hello World with Docker
    @Test
    public void shouldBuildPython3FunctionWithDocker() {
        runContainer(greengrassITShared.getPython3HelloWorldDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);
    }

    // Test set 10: Expected failure redeploying group without being able to create new keys with Python 3 Hello World with Docker
    @Test
    public void shouldFailToRedeployWithoutCreatingNewKeysWithPython3FunctionWithDocker() {
        // Make sure we have a fresh group, not one reused from another test
        Optional<String> name = Optional.of(ioHelper.getUuid());

        // Expect that the first container does not return an error
        GenericContainer genericContainer = runContainer(greengrassITShared.getPython3HelloWorldDeploymentCommandWithoutForceNewKeys(name), EXIT_CODE_IS_ZERO);

        // Stop the first container
        genericContainer.stop();

        // Expect that the second container returns an error
        runContainer(greengrassITShared.getPython3HelloWorldDeploymentCommandWithoutForceNewKeys(name), EXIT_CODE_IS_NOT_ZERO);
    }

    // Test set 11: Reuse Python 3 Hello World in another group with Docker
    @Test
    public void shouldReusePython3FunctionWithDocker() throws IOException {
        File tempDeploymentConfFile = greengrassITShared.setupReusedFunctionDeploymentConf(Optional.of(AWS_GREENGRASS_LAMBDA_FUNCTIONS_VIA_PARENT_DIRECTORY), GreengrassITShared.HELLO_WORLD_PYTHON_3_PROD_PARTIAL, greengrassITShared.getGroupName());

        // Build it
        runContainer(greengrassITShared.getPython3HelloWorldDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);

        // Try to reuse it
        runContainer(greengrassITShared.getReusedFunctionDeploymentCommand(tempDeploymentConfFile), EXIT_CODE_IS_ZERO);
    }

    // Test set 12: Reuse Java benchmark in another group with Docker
    @Test
    public void shouldReuseJavaFunctionWithDocker() throws IOException {
        File tempDeploymentConfFile = greengrassITShared.setupReusedFunctionDeploymentConf(Optional.of(AWS_GREENGRASS_LAMBDA_FUNCTIONS_VIA_PARENT_DIRECTORY), GreengrassITShared.BENCHMARK_JAVA_PROD_PARTIAL, greengrassITShared.getGroupName());

        // Build it
        runContainer(greengrassITShared.getBenchmarkDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);

        // Try to reuse it
        runContainer(greengrassITShared.getReusedFunctionDeploymentCommand(tempDeploymentConfFile), EXIT_CODE_IS_ZERO);
    }

    // Test set 13: Fail to reuse Java benchmark in another group with Docker
    @Test
    public void shouldFailToReuseBroadPatternFunctionWithDocker() throws IOException {
        File tempDeploymentConfFile = greengrassITShared.setupReusedFunctionDeploymentConf(Optional.of(AWS_GREENGRASS_LAMBDA_FUNCTIONS_VIA_PARENT_DIRECTORY), GreengrassITShared.BENCHMARK_PROD_PARTIAL, greengrassITShared.getGroupName());

        // Build it
        runContainer(greengrassITShared.getBenchmarkDeploymentCommand(Optional.empty()), EXIT_CODE_IS_ZERO);

        // Try to reuse it
        runContainer(greengrassITShared.getReusedFunctionDeploymentCommand(tempDeploymentConfFile), EXIT_CODE_IS_NOT_ZERO);
    }

    private GenericContainer runContainer(String arguments, Matcher<Integer> integerMatcher) {
        GenericContainer genericContainer = startAndGetContainer(arguments);
        waitForContainerToFinish(genericContainer);

        System.out.println(genericContainer.getLogs());
        Assert.assertThat(genericContainer.getCurrentContainerInfo().getState().getExitCode(), is(integerMatcher));

        return genericContainer;
    }
}
