package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.ecr.EcrClient;

import javax.inject.Inject;

public class EcrClientProvider implements SafeProvider<EcrClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public EcrClientProvider() {
    }

    @Override
    public EcrClient get() {
        return safeGet(sdkErrorHandler);
    }

    public EcrClient unsafeGet() {
        return EcrClient.create();
    }
}