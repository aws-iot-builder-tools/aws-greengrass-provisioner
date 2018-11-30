package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import software.amazon.awssdk.services.iam.model.Role;

public class BuildableNodeFunction extends BuildableFunction {
    public BuildableNodeFunction(FunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
