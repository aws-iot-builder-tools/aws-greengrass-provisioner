package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.QueryArguments;

public interface QueryArgumentHelper {
    void displayUsage();

    QueryArguments parseArguments(String[] args);
}
