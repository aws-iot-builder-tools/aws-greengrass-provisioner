package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LambdaFunctionArnInfoAndFunctionConf {
    private final LambdaFunctionArnInfo lambdaFunctionArnInfo;
    private final FunctionConf functionConf;
}
