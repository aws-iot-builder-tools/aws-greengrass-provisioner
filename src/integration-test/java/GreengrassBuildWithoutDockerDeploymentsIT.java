import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class GreengrassBuildWithoutDockerDeploymentsIT {
    private static Logger log = LoggerFactory.getLogger(GreengrassBuildWithoutDockerDeploymentsIT.class);
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public ExpectedSystemExit expectedSystemExit = ExpectedSystemExit.none();
    GreengrassITShared greengrassITShared;

    @Before
    public void beforeTestSetup() throws IOException {
        greengrassITShared = new GreengrassITShared();
        GreengrassITShared.beforeTestSetup();
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
    @Test
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
}
