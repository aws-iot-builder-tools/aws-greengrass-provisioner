package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.QueryArguments;

public interface QueryArgumentHelper {
    void displayUsage();

    QueryArguments parseArguments(String[] args);
}
