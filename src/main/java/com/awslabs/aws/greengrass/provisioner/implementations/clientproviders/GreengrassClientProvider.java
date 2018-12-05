package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.greengrass.GreengrassClient;

import javax.inject.Inject;

public class GreengrassClientProvider implements SafeProvider<GreengrassClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public GreengrassClientProvider() {
    }

    @Override
    public GreengrassClient get() {
        return safeGet(sdkErrorHandler);
    }

    public GreengrassClient unsafeGet() {
        return GreengrassClient.create();
    }
}