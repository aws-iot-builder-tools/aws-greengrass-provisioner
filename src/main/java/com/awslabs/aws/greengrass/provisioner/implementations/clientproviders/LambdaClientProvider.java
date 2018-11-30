package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.lambda.LambdaClient;

import javax.inject.Inject;

public class LambdaClientProvider implements SafeProvider<LambdaClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public LambdaClientProvider() {
    }

    @Override
    public LambdaClient get() {
        return safeGet(sdkErrorHandler);
    }

    public LambdaClient unsafeGet() {
        return LambdaClient.create();
    }
}
