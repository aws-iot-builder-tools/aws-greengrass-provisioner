package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalSageMakerResource implements LocalResource {
    public abstract String getArn();
}
