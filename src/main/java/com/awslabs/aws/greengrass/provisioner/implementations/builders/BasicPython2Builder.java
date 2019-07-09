package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.interfaces.builders.Python2Builder;

public class BasicPython2Builder extends BasicPythonBuilder implements Python2Builder {
    public String getPip() {
        return "pip";
    }
}
