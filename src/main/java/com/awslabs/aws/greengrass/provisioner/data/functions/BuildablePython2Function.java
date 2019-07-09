package com.awslabs.aws.greengrass.provisioner.data.functions;

import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import software.amazon.awssdk.services.iam.model.Role;

public class BuildablePython2Function extends BuildableFunction {
    public BuildablePython2Function(ModifiableFunctionConf functionConf, Role lambdaRole) {
        super(functionConf, lambdaRole);
    }
}
