package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.conf.DeploymentConf;
import com.awslabs.iot.data.GreengrassGroupId;
import com.awslabs.iot.data.GreengrassGroupName;
import com.awslabs.iot.data.ThingName;
import software.amazon.awssdk.services.iam.model.Role;

import java.util.Optional;

public interface DeploymentHelper extends Operation<DeploymentArguments> {
    String EMPTY = "EMPTY";

    DeploymentConf getDeploymentConf(ThingName coreThingName, DeploymentArguments deploymentArguments, GreengrassGroupName greengrassGroupName);

    void createAndWaitForDeployment(Optional<Role> greengrassServiceRole, Optional<Role> greengrassRole, GreengrassGroupId greengrassGroupId, String groupVersionId);

    void associateRoleToGroup(Role greengrassRole, GreengrassGroupId greengrassGroupId);

    void associateServiceRoleToAccount(Role greengrassServiceRole);
}
