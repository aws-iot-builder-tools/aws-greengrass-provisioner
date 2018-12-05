package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;

import javax.inject.Inject;

public class IamClientProvider implements SafeProvider<IamClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public IamClientProvider() {
    }

    @Override
    public IamClient get() {
        return safeGet(sdkErrorHandler);
    }

    public IamClient unsafeGet() {
        return IamClient.builder()
                .region(Region.AWS_GLOBAL)
                .build();
    }
}
