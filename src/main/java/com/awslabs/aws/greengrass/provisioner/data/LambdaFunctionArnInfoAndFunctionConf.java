package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import lombok.Builder;
import lombok.Data;

import java.util.Optional;

@Data
@Builder
public class LambdaFunctionArnInfoAndFunctionConf {
    private final LambdaFunctionArnInfo lambdaFunctionArnInfo;
    private final FunctionConf functionConf;
    @Builder.Default private final Optional<String> error = Optional.empty();
}
