import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import com.awslabs.aws.greengrass.provisioner.lambda.AwsGreengrassProvisionerLambda;
import com.awslabs.aws.greengrass.provisioner.lambda.LambdaInput;
import com.awslabs.iot.data.ImmutablePolicyDocument;
import com.awslabs.iot.data.ImmutablePolicyName;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsMapContaining;
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
    private V2IotHelper v2IotHelper;
    private AwsGreengrassProvisionerLambda awsGreengrassProvisionerLambda;
    private IoHelper ioHelper;

    @Before
    public void setup() {
        v2IotHelper = AwsGreengrassProvisioner.getInjector().v2IotHelper();
        ioHelper = AwsGreengrassProvisioner.getInjector().ioHelper();
        awsGreengrassProvisionerLambda = new AwsGreengrassProvisionerLambda();
    }

    @Test
    public void shouldReturnAllExpectedKeysAndSupportPrivateKeyLocationSubstitution() {
        v2IotHelper.createPolicyIfNecessary(ImmutablePolicyName.builder().name(GREENGRASS_DEFAULT_POLICY_NAME).build(),
                ImmutablePolicyDocument.builder().document(GREENGRASS_DEFAULT_POLICY).build());
        LambdaInput lambdaInput = new LambdaInput();
        lambdaInput.GroupName = ioHelper.getUuid();
        lambdaInput.CoreRoleName = "Greengrass_CoreRole";
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