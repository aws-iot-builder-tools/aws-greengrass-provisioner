package com.awslabs.aws.greengrass.provisioner.data.resources;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalS3Resource {
    private final String name;
    private final String uri;
    private final String path;
}
