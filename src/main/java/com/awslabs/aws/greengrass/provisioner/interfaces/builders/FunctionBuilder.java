package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.FunctionConf;

public interface FunctionBuilder {
    void verifyHandlerExists(FunctionConf functionConf);
}
