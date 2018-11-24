package com.awslabs.aws.greengrass.provisioner.data;

import com.amazonaws.services.identitymanagement.model.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BuildableFunction {
    private final FunctionConf functionConf;
    private final Role lambdaRole;
}
