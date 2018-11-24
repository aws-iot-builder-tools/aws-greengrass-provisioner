package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeploymentArguments;

public interface DeploymentArgumentHelper {
    String DEFAULTS_CONF = "defaults.conf";

    void displayUsage();

    DeploymentArguments parseArguments(String[] args);
}
