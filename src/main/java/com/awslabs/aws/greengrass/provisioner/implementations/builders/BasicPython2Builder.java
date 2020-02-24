package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.interfaces.builders.Python2Builder;

import javax.inject.Inject;

public class BasicPython2Builder extends BasicPythonBuilder implements Python2Builder {
    @Inject
    public BasicPython2Builder() {
    }

    public String getPip() {
        return "pip";
    }
}
