package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalDeviceResource {
    public abstract String getName();

    public abstract String getPath();

    public abstract boolean isReadWrite();
}
