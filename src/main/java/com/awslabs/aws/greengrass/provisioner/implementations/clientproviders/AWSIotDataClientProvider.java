package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.iotdata.AWSIotDataClient;
import com.amazonaws.services.iotdata.AWSIotDataClientBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AWSIotDataClientProvider implements SafeProvider<AWSIotDataClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AWSIotDataClientProvider() {
    }

    @Override
    public AWSIotDataClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AWSIotDataClient unsafeGet() {
        return (AWSIotDataClient) AWSIotDataClientBuilder.defaultClient();
    }
}
