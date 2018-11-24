package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.services.identitymanagement.model.Role;
import com.awslabs.aws.greengrass.provisioner.data.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.DeploymentConf;

import java.util.Optional;

public interface DeploymentHelper {
    String CORE_SUB_NAME = "core";

    DeploymentConf getDeploymentConf(String deploymentConfigFilename, String groupName);

    boolean createAndWaitForDeployment(java.util.Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId);

    void associateRoleToGroup(Role greengrassRole, String groupId);

    void associateServiceRoleToAccount(Role greengrassServiceRole);

    void doDeployment(DeploymentArguments deploymentArguments);
}
