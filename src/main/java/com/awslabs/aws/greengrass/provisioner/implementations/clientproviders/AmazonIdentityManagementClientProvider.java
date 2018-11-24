package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AmazonIdentityManagementClientProvider implements SafeProvider<AmazonIdentityManagementClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AmazonIdentityManagementClientProvider() {
    }

    @Override
    public AmazonIdentityManagementClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AmazonIdentityManagementClient unsafeGet() {
        return (AmazonIdentityManagementClient) AmazonIdentityManagementClientBuilder.defaultClient();
    }
}
