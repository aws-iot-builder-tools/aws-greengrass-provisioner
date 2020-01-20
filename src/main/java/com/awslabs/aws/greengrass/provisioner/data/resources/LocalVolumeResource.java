package com.awslabs.aws.greengrass.provisioner.data.resources;

import org.immutables.value.Value;

@Value.Immutable
public abstract class LocalVolumeResource implements LocalResource {
    public abstract String getSourcePath();

    public String getPath() {
        return String.join("-", getSourcePath(), getDestinationPath());
    }

    public abstract String getDestinationPath();

    public abstract boolean isReadWrite();
}
