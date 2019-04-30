package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import javax.inject.Inject;

public class AwsCredentialsProvider implements SafeProvider<AwsCredentials> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    DefaultCredentialsProvider defaultCredentialsProvider;

    @Override
    public AwsCredentials unsafeGet() {
        return defaultCredentialsProvider.resolveCredentials();
    }

    @Override
    public AwsCredentials get() {
        return safeGet(sdkErrorHandler);
    }
}
