package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.sts.StsClient;

import javax.inject.Inject;

public class StsClientProvider implements SafeProvider<StsClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public StsClientProvider() {
    }

    @Override
    public StsClient get() {
        return safeGet(sdkErrorHandler);
    }

    public StsClient unsafeGet() {
        return StsClient.create();
    }
}