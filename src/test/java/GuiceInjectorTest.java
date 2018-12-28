import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import org.junit.Before;
import org.junit.Test;

public class GuiceInjectorTest {
    @Before
    public void setup() {
        // Prevents DefaultAwsRegionProviderChain failures in environments with no AWS configuration
        System.setProperty("aws.region", "us-east-1");
    }

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
