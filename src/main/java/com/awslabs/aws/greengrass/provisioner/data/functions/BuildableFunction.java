package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.amazonaws.services.identitymanagement.model.Role;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BuildableFunction {
    private final FunctionConf functionConf;
    private final Role lambdaRole;
}
