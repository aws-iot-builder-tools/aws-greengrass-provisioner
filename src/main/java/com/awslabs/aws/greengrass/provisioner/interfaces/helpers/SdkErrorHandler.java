package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import software.amazon.awssdk.core.exception.SdkClientException;

public interface SdkErrorHandler {
    Void handleSdkError(SdkClientException e);
}
