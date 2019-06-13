package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalSageMakerResource {
    public abstract String getName();

    public abstract String getArn();

    public abstract String getPath();
}
