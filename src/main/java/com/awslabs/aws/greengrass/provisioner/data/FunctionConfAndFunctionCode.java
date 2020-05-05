package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import org.immutables.value.Value;
import software.amazon.awssdk.services.lambda.model.FunctionCode;

@Value.Immutable
public abstract class FunctionConfAndFunctionCode {
    public abstract FunctionConf functionConf();

    public abstract FunctionCode functionCode();
}
