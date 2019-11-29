package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalSecretsManagerResource {
    public abstract String getResourceName();

    public abstract String getArn();

    public abstract String getSecretName();
}
