package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import software.amazon.awssdk.services.iam.model.Role;

import java.util.Optional;

public interface DeploymentHelper {
    String CORE_SUB_NAME = "core";

    DeploymentConf getDeploymentConf(String deploymentConfigFilename, String groupName);

    boolean createAndWaitForDeployment(java.util.Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId);

    void associateRoleToGroup(Role greengrassRole, String groupId);

    void associateServiceRoleToAccount(Role greengrassServiceRole);

    void doDeployment(DeploymentArguments deploymentArguments);
}
