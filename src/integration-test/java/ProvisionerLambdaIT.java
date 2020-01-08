import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import com.awslabs.aws.greengrass.provisioner.lambda.AwsGreengrassProvisionerLambda;
import com.awslabs.aws.greengrass.provisioner.lambda.LambdaInput;
import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsMapContaining;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;

public class ProvisionerLambdaIT {
    private static final String NEW_KEY_PATH = "/secretstuff/key.key";
    private static final String GREENGRASS_DEFAULT_POLICY = "{\n" +
            "  \"Statement\": [\n" +
            "    {\n" +
            "      \"Action\": [\n" +
            "        \"iot:*\",\n" +
            "        \"greengrass:*\"\n" +
            "      ],\n" +
            "      \"Effect\": \"Allow\",\n" +
            "      \"Resource\": [\n" +
            "        \"*\"\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"Version\": \"2012-10-17\"\n" +
            "}";
    private static final String GREENGRASS_DEFAULT_POLICY_NAME = "GreengrassDefaultPolicy";
    private IotHelper iotHelper;
    private AwsGreengrassProvisionerLambda awsGreengrassProvisionerLambda;
    private IoHelper ioHelper;

    @Before
    public void setup() {
        iotHelper = AwsGreengrassProvisioner.getInjector().getInstance(IotHelper.class);
        ioHelper = AwsGreengrassProvisioner.getInjector().getInstance(IoHelper.class);
        awsGreengrassProvisionerLambda = new AwsGreengrassProvisionerLambda();
    }

    @Test
    public void shouldReturnAllExpectedKeysAndSupportPrivateKeyLocationSubstitution() {
        iotHelper.createPolicyIfNecessary(GREENGRASS_DEFAULT_POLICY_NAME, GREENGRASS_DEFAULT_POLICY);
        LambdaInput lambdaInput = new LambdaInput();
        lambdaInput.GroupName = ioHelper.getUuid();
        lambdaInput.CoreRoleName = "GreengrassCoreRole";
        lambdaInput.ServiceRoleExists = true;
        lambdaInput.CorePolicyName = GREENGRASS_DEFAULT_POLICY_NAME;
        lambdaInput.KeyPath = NEW_KEY_PATH;

        Map<String, String> oem = awsGreengrassProvisionerLambda.handleRequest(lambdaInput, null);
        MatcherAssert.assertThat(oem, IsMapContaining.hasKey("certs/core.crt"));
        MatcherAssert.assertThat(oem, IsMapContaining.hasKey("certs/core.key"));
        MatcherAssert.assertThat(oem, IsMapContaining.hasKey(AwsGreengrassProvisionerLambda.CONFIG_JSON_KEY));
        MatcherAssert.assertThat(oem, IsMapContaining.hasKey("certs/root.ca.pem"));

        String configJson = oem.get(AwsGreengrassProvisionerLambda.CONFIG_JSON_KEY);

        MatcherAssert.assertThat(configJson, containsString(NEW_KEY_PATH));
    }
}