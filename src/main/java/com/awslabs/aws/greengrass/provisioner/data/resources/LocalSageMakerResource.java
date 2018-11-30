package com.awslabs.aws.greengrass.provisioner.data.resources;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalSageMakerResource {
    private final String name;
    private final String arn;
    private final String path;
}
