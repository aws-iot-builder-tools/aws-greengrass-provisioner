package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.interfaces.builders.Python3Builder;

public class BasicPython3Builder extends BasicPythonBuilder implements Python3Builder {
    public String getPip() {
        return "pip3";
    }
}
