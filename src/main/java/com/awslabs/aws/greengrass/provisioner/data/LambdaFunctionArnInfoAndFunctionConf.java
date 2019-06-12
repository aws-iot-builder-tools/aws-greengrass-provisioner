package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public abstract class LambdaFunctionArnInfoAndFunctionConf {
    public abstract LambdaFunctionArnInfo getLambdaFunctionArnInfo();

    public abstract ModifiableFunctionConf getFunctionConf();

    public abstract Optional<String> getError();
}
