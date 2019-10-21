package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public abstract class LambdaFunctionArnInfoAndFunctionConf {
    public abstract LambdaFunctionArnInfo getLambdaFunctionArnInfo();

    public abstract FunctionConf getFunctionConf();

    public abstract Optional<String> getError();
}
