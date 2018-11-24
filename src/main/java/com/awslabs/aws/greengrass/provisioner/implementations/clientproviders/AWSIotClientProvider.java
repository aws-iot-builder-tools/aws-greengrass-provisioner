package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.AWSIotClientBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AWSIotClientProvider implements SafeProvider<AWSIotClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AWSIotClientProvider() {
    }

    @Override
    public AWSIotClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AWSIotClient unsafeGet() {
        return (AWSIotClient) AWSIotClientBuilder.defaultClient();
    }
}
