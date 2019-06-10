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
        AwsGreengrassProvisioner.main(split(greengrassITShared.FAIL_DEPLOYMENT_COMMAND));
    }

    // Test set 2: Expected success with CDD skeleton without Docker
    @Test
    public void shouldBuildJavaFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(greengrassITShared.CDD_SKELETON_DEPLOYMENT_COMMAND));
    }

    // Test set 3: Expected success with Python Hello World without Docker
    @Test
    public void shouldBuildPythonFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(greengrassITShared.PYTHON_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 4: Expected success with Python LiFX function (has dependencies to fetch) without Docker
    @Test
    public void shouldBuildPythonFunctionWithDependenciesWithoutDocker() {
        AwsGreengrassProvisioner.main(split(greengrassITShared.LIFX_DEPLOYMENT_COMMAND));
    }

    // Test set 5: Expected success with Node Hello World without Docker
    @Test
    public void shouldBuildNodeFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(greengrassITShared.NODE_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 6: Expected success with all three languages in one build without Docker
    @Test
    public void shouldBuildCombinedFunctionWithoutDocker() {
        AwsGreengrassProvisioner.main(split(greengrassITShared.ALL_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 7: Expected failure to launch an EC2 instance with ARM32 without Docker
    @Test
    public void shouldFailEc2LaunchWithArm32WithoutDocker() {
        expectedSystemExit.expectSystemExitWithStatus(1);
        AwsGreengrassProvisioner.main(split(greengrassITShared.EC2_ARM32_NODE_HELLO_WORLD_DEPLOYMENT_COMMAND));
    }

    // Test set 8: Expected success with Node Express function (has dependencies to fetch) without Docker
    @Test
    public void shouldBuildNodeFunctionWithDependenciesWithoutDocker() {
        AwsGreengrassProvisioner.main(split(greengrassITShared.NODE_WEBSERVER_DEPLOYMENT_COMMAND));
    }

    private String[] split(String input) {
        return input.split(" ");
    }
}
