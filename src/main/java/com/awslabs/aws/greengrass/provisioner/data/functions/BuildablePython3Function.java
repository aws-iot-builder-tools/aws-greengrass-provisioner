package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import software.amazon.awssdk.services.iam.model.Role;

public class BuildablePython3Function extends BuildableFunction {
    public BuildablePython3Function(ModifiableFunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
