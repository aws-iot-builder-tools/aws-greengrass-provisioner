package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.greengrass.model.GetDeploymentStatusResponse;

import java.util.Arrays;

public class BasicGreengrassHelperTest {
    public static final String CONFIGURATION_PARAMETER = "configuration parameter";
    public static final String DOES_NOT_MATCH_REQUIRED_PATTERN = "does not match required pattern";
    private BasicGreengrassHelper basicGreengrassHelper;

    @Before
    public void setup() {
        basicGreengrassHelper = new BasicGreengrassHelper();
    }

    @Test
    public void shouldReturnTrueWithBothValues() {
        // This test is to validate the behavior of some new error handling logic
        GetDeploymentStatusResponse getDeploymentStatusResponse = GetDeploymentStatusResponse.builder()
                .errorMessage(String.join("", "---- ", CONFIGURATION_PARAMETER, " ---- ", DOES_NOT_MATCH_REQUIRED_PATTERN, " ---- "))
                .build();

        Assert.assertTrue(basicGreengrassHelper.shouldRedeploy(getDeploymentStatusResponse, Arrays.asList(CONFIGURATION_PARAMETER, DOES_NOT_MATCH_REQUIRED_PATTERN), "One or more configuration parameters were specified that did not match the allowed patterns. Adjust the values and try again."));
    }

    @Test
    public void shouldReturnFalseWithOnlyOneValue() {
        // This test is to validate the behavior of some new error handling logic
        GetDeploymentStatusResponse getDeploymentStatusResponse1 = GetDeploymentStatusResponse.builder()
                .errorMessage(String.join("", "---- ", CONFIGURATION_PARAMETER, " ---- "))
                .build();

        Assert.assertFalse(basicGreengrassHelper.shouldRedeploy(getDeploymentStatusResponse1, Arrays.asList(CONFIGURATION_PARAMETER, DOES_NOT_MATCH_REQUIRED_PATTERN), "One or more configuration parameters were specified that did not match the allowed patterns. Adjust the values and try again."));

        GetDeploymentStatusResponse getDeploymentStatusResponse2 = GetDeploymentStatusResponse.builder()
                .errorMessage(String.join("", "---- ", DOES_NOT_MATCH_REQUIRED_PATTERN, " ---- "))
                .build();

        Assert.assertFalse(basicGreengrassHelper.shouldRedeploy(getDeploymentStatusResponse2, Arrays.asList(CONFIGURATION_PARAMETER, DOES_NOT_MATCH_REQUIRED_PATTERN), "One or more configuration parameters were specified that did not match the allowed patterns. Adjust the values and try again."));
    }
}
