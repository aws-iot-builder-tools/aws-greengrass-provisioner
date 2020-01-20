package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalDeviceResource implements LocalResource {
    public abstract boolean isReadWrite();
}
