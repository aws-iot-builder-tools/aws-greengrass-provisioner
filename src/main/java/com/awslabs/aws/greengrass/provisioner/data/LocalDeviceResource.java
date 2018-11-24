package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalDeviceResource {
    private final String name;
    private final String path;
    private final boolean readWrite;
}
