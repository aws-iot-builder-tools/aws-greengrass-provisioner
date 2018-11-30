package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.UpdateArguments;

public interface UpdateArgumentHelper {
    void displayUsage();

    UpdateArguments parseArguments(String[] args);
}
