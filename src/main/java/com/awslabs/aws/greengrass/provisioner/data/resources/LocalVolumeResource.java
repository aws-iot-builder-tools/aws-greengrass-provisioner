package com.awslabs.aws.greengrass.provisioner.data.resources;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalVolumeResource {
    private final String name;
    private final String sourcePath;
    private final String destinationPath;
    private final boolean readWrite;
}
