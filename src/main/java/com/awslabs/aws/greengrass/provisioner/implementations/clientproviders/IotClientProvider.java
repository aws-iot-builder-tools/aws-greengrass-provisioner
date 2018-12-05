package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.iot.IotClient;

import javax.inject.Inject;

public class IotClientProvider implements SafeProvider<IotClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public IotClientProvider() {
    }

    @Override
    public IotClient get() {
        return safeGet(sdkErrorHandler);
    }

    public IotClient unsafeGet() {
        return IotClient.create();
    }
}