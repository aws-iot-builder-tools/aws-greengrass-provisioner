package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;

import javax.inject.Inject;

public class AWSLambdaClientProvider implements SafeProvider<AWSLambdaClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public AWSLambdaClientProvider() {
    }

    @Override
    public AWSLambdaClient get() {
        return safeGet(sdkErrorHandler);
    }

    public AWSLambdaClient unsafeGet() {
        return (AWSLambdaClient) AWSLambdaClientBuilder.defaultClient();
    }
}
