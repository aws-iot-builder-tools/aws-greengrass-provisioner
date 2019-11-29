package com.awslabs.aws.greengrass.provisioner.implementations.clientproviders;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SafeProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SdkErrorHandler;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Inject;

public class SecretsManagerClientProvider implements SafeProvider<SecretsManagerClient> {
    @Inject
    SdkErrorHandler sdkErrorHandler;

    @Inject
    public SecretsManagerClientProvider() {
    }

    @Override
    public SecretsManagerClient get() {
        return safeGet(sdkErrorHandler);
    }

    public SecretsManagerClient unsafeGet() {
        return SecretsManagerClient.create();
    }
}