package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalS3Resource {
    public abstract String getName();

    public abstract String getUri();

    public abstract String getPath();
}
