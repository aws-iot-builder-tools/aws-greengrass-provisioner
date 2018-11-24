package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AmazonCloudFormationClientProvider implements SafeProvider<AmazonCloudFormationClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AmazonCloudFormationClientProvider() {
    }

    @Override
    public AmazonCloudFormationClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AmazonCloudFormationClient unsafeGet() {
        return (AmazonCloudFormationClient) AmazonCloudFormationClientBuilder.defaultClient();
    }
}
