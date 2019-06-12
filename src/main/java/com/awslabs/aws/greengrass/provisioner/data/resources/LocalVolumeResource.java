package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalVolumeResource {
    public abstract String getName();

    public abstract String getSourcePath();

    public abstract String getDestinationPath();

    public abstract boolean isReadWrite();
}
