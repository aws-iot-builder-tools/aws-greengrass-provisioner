package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;

public interface ExecutableBuilder extends FunctionBuilder {
    void buildExecutableFunctionIfNecessary(FunctionConf functionConf);
}
