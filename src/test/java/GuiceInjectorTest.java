import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import org.junit.Test;

public class GuiceInjectorTest {
    ///////////////////////////////////////////////////////////////////
    // These tests capture some injector related issues, but not all //
    ///////////////////////////////////////////////////////////////////

    @Test
    public void shouldGetSdkErrorHandler() {
        AwsGreengrassProvisioner.getSdkErrorHandler();
    }

    @Test
    public void shouldGetAwsGreengrassProvisioner() {
        AwsGreengrassProvisioner.getAwsGreengrassProvisioner();
    }
}
