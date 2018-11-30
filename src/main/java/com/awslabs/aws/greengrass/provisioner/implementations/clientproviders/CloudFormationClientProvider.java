package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;

import javax.inject.Inject;

public class CloudFormationClientProvider implements SafeProvider<CloudFormationClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public CloudFormationClientProvider() {
    }

    @Override
    public CloudFormationClient get() {
        return safeGet(sdkErrorHandler);
    }

    public CloudFormationClient unsafeGet() {
        return CloudFormationClient.create();
    }
}
