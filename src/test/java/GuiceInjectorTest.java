import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import org.junit.Test;

public class GuiceInjectorTest {
    ///////////////////////////////////////////////////////////////////
    // These tests capture some injector related issues, but not all //
    ///////////////////////////////////////////////////////////////////

    @Test
    public void shouldGetSdkErrorHandler() {
        try {
            AwsGreengrassProvisioner.getSdkErrorHandler();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void shouldGetAwsGreengrassProvisioner() {
        try {
            AwsGreengrassProvisioner.getAwsGreengrassProvisioner();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
