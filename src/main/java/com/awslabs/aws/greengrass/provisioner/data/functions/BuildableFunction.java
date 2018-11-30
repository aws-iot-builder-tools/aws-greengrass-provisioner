package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import lombok.Builder;
import lombok.Data;
import software.amazon.awssdk.services.iam.model.Role;

@Data
@Builder
public class BuildableFunction {
    private final FunctionConf functionConf;
    private final Role lambdaRole;
}
