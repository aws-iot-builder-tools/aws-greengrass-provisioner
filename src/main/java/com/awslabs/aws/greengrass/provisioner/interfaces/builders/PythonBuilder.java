package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.FunctionConf;

public interface PythonBuilder extends ScriptingFunctionBuilder {
    String getArchivePath(FunctionConf functionConf);
}
