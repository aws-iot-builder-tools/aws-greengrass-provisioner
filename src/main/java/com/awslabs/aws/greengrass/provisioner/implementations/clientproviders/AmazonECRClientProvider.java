package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AmazonECRClientProvider implements SafeProvider<AmazonECRClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AmazonECRClientProvider() {
    }

    @Override
    public AmazonECRClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AmazonECRClient unsafeGet() {
        return (AmazonECRClient) AmazonECRClientBuilder.defaultClient();
    }
}
