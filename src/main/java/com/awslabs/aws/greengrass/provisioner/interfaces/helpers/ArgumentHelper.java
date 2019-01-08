package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.Arguments;

public interface ArgumentHelper<T extends Arguments> {
    void displayUsage();

    T parseArguments(String[] args);
}
