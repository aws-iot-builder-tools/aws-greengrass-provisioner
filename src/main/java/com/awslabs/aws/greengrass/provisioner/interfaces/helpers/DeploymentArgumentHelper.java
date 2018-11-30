package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;

public interface DeploymentArgumentHelper {
    String DEFAULTS_CONF = "defaults.conf";

    void displayUsage();

    DeploymentArguments parseArguments(String[] args);
}
