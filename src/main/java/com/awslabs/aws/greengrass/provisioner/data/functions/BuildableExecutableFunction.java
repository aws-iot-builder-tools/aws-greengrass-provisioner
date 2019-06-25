package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import software.amazon.awssdk.services.iam.model.Role;

public class BuildableExecutableFunction extends BuildableFunction {
    public BuildableExecutableFunction(ModifiableFunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
