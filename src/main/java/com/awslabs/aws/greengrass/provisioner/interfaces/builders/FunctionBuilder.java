package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;

import java.util.Optional;

public interface FunctionBuilder {
    Optional<String> verifyHandlerExists(FunctionConf functionConf);
}
