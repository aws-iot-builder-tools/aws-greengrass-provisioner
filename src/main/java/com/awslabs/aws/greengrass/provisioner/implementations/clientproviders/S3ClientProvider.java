package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Inject;

public class S3ClientProvider implements SafeProvider<S3Client> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public S3ClientProvider() {
    }

    @Override
    public S3Client get() {
        return safeGet(sdkErrorHandler);
    }

    public S3Client unsafeGet() {
        return S3Client.create();
    }
}