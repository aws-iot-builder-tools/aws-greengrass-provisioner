package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.greengrass.AWSGreengrassClient;
import com.amazonaws.services.greengrass.AWSGreengrassClientBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AWSGreengrassClientProvider implements SafeProvider<AWSGreengrassClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AWSGreengrassClientProvider() {
    }

    @Override
    public AWSGreengrassClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AWSGreengrassClient unsafeGet() {
        return (AWSGreengrassClient) AWSGreengrassClientBuilder.defaultClient();
    }
}
