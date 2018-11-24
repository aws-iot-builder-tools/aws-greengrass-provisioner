package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.SdkClientException;

public interface SdkErrorHandler {
    void handleSdkError(SdkClientException e);
}
