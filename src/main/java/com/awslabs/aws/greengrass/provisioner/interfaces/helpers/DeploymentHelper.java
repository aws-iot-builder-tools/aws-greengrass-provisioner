package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import software.amazon.awssdk.services.iam.model.Role;

import java.util.Optional;

public interface DeploymentHelper extends Operation<DeploymentArguments> {
    String GREENGRASS_SERVICE_ROLE_NAME = "Greengrass_ServiceRole";
    String CORE_SUB_NAME = "core";

    DeploymentConf getDeploymentConf(String deploymentConfigFilename, String groupName);

    Void createAndWaitForDeployment(Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, String groupId, String groupVersionId);

    void associateRoleToGroup(Role greengrassRole, String groupId);

    void associateServiceRoleToAccount(Role greengrassServiceRole);
}
