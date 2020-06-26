import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class GreengrassBuildWithoutDockerDeploymentsIT {
    private static Logger log = LoggerFactory.getLogger(GreengrassBuildWithoutDockerDeploymentsIT.class);
    @Rule
    public ExpectedSystemExit expectedSystemExit = ExpectedSystemExit.none();
    GreengrassITShared greengrassITShared;
    private IoHelper ioHelper;

    @Before
    public void beforeTestSetup() throws IOException {
        greengrassITShared = new GreengrassITShared();
        GreengrassITShared.beforeTestSetup();
        ioHelper = AwsGreengrassProvisioner.getInjector().ioHelper();
    }

    @After
    public void afterTestTeardown() throws IOException {
        GreengrassITShared.cleanDirectories();
    }

    // Test set 1: Expected failure with invalid deployment name without Docker
    @Test
    public void shouldFailWithoutDocker() {
        expectedSystemExit.expectSystemExitWithStatus(1);
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getFailDeploymentCommand(Optional.empty())));
    }

    // Test set 2: Expected success with CDD skeleton without Docker
    @Test
    public void shouldBuildJavaFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getCddSkeletonDeploymentCommand(Optional.empty())));
    }

    // Test set 3: Expected success with Python 2 Hello World without Docker
    // NOTE: Only testing Python 2 in Docker now as many hosts no longer support it
    @Test
    @Ignore
    public void shouldBuildPython2FunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getPython2HelloWorldDeploymentCommand(Optional.empty())));
    }

    // Test set 4: Expected success with Python LiFX function (has dependencies to fetch) without Docker
    @Test
    public void shouldBuildPythonFunctionWithDependenciesWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getLifxDeploymentCommand(Optional.empty())));
    }

    // Test set 5: Expected success with Node Hello World without Docker
    @Test
    public void shouldBuildNodeFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getNodeHelloWorldDeploymentCommand(Optional.empty())));
    }

    // Test set 6: Expected success with all three languages in one build without Docker
    @Test
    public void shouldBuildCombinedFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getAllHelloWorldDeploymentCommand(Optional.empty())));
    }

    // Test set 7: Expected failure to launch an EC2 instance with ARM32 without Docker
    @Test
    public void shouldFailEc2LaunchWithArm32WithoutDocker() {
        expectedSystemExit.expectSystemExitWithStatus(1);
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getEc2Arm32NodeHelloWorldDeploymentCommand(Optional.empty())));
    }

    // Test set 8: Expected success with Node Express function (has dependencies to fetch) without Docker
    @Test
    public void shouldBuildNodeFunctionWithDependenciesWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getNodeWebserverDeploymentCommand(Optional.empty())));
    }

    // Test set 9: Expected success with X86_64 Sample C function without Docker
    @Test
    public void shouldBuildX86_64SampleCWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getX86_64SampleCDeploymentCommand(Optional.empty())));
    }

    // Test set 10: Expected success with Python 3 Hello World without Docker
    @Test
    public void shouldBuildPython3FunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getPython3HelloWorldDeploymentCommand(Optional.empty())));
    }

    // Test set 11: Expect success redeploying group without being able to create new keys with Python 3 Hello World without Docker
    @Test
    public void shouldFailToRedeployWithoutCreatingNewKeysWithPython3FunctionWithoutDocker() throws IOException {
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getPython3HelloWorldDeploymentCommandWithoutForceNewKeys(Optional.empty())));
        // Clean the directories so we don't have the credentials anymore
        GreengrassITShared.beforeTestSetup();
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getPython3HelloWorldDeploymentCommandWithoutForceNewKeys(Optional.empty())));
    }

    // Test set 12: Reuse Python 3 Hello World in another group without Docker
    @Test
    public void shouldReusePython3FunctionWithoutDocker() throws IOException {
        File tempDeploymentConfFile = greengrassITShared.setupReusedFunctionDeploymentConf(Optional.empty(), GreengrassITShared.HELLO_WORLD_PYTHON_3_PROD_PARTIAL, greengrassITShared.getGroupName());

        // Build it the first time
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getPython3HelloWorldDeploymentCommand(Optional.empty())));

        // Reuse it
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getReusedFunctionDeploymentCommand(tempDeploymentConfFile)));
    }

    // Test set 13: Reuse Java function in another group without Docker
    @Test
    public void shouldReuseJavaFunctionWithoutDocker() throws IOException {
        File tempDeploymentConfFile = greengrassITShared.setupReusedFunctionDeploymentConf(Optional.empty(), GreengrassITShared.BENCHMARK_JAVA_PROD_PARTIAL, greengrassITShared.getGroupName());

        // Build it the first time
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getBenchmarkDeploymentCommand(Optional.empty())));

        // Reuse it
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getReusedFunctionDeploymentCommand(tempDeploymentConfFile)));
    }

    // Test set 14: Reuse a function that has a name that is too broad and fail
    @Test
    public void shouldFailToReuseBroadPatternWithoutDocker() throws IOException {
        expectedSystemExit.expectSystemExitWithStatus(1);

        File tempDeploymentConfFile = greengrassITShared.setupReusedFunctionDeploymentConf(Optional.empty(), GreengrassITShared.BENCHMARK_PROD_PARTIAL, greengrassITShared.getGroupName());

        // Build it the first time
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getBenchmarkDeploymentCommand(Optional.empty())));

        // Reuse it
        AwsGreengrassProvisioner.main(greengrassITShared.split(greengrassITShared.getReusedFunctionDeploymentCommand(tempDeploymentConfFile)));
    }
}
