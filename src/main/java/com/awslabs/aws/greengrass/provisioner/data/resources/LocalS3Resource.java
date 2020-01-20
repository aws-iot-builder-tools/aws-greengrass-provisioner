package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalS3Resource implements LocalResource {
    public abstract String getUri();
}
