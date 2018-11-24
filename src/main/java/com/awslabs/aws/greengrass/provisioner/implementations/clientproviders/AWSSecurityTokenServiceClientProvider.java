package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AWSSecurityTokenServiceClientProvider implements SafeProvider<AWSSecurityTokenServiceClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AWSSecurityTokenServiceClientProvider() {
    }

    @Override
    public AWSSecurityTokenServiceClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AWSSecurityTokenServiceClient unsafeGet() {
        return (AWSSecurityTokenServiceClient) AWSSecurityTokenServiceClient.builder().build();
    }
}
