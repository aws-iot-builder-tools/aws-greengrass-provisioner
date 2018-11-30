package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.services.greengrass.model.*;
import com.amazonaws.services.identitymanagement.model.Role;
import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface GreengrassHelper {
    void associateServiceRoleToAccount(Role role);

    Optional<GroupInformation> getGroupInformation(String groupNameOrGroupId);

    String createGroupIfNecessary(String groupName);

    void associateRoleToGroup(String groupId, Role greengrassRole);

    String createCoreDefinitionAndVersion(String coreDefinitionName, String coreCertificateArn, String coreThingArn);

    Function buildFunctionModel(String functionArn, FunctionConf functionConf);

    Function buildFunctionModel(String functionArn, com.amazonaws.services.lambda.model.FunctionConfiguration lambdaFunctionConfiguration, Map<String, String> defaultEnvironment, EncodingType encodingType, boolean pinned);

    String createFunctionDefinitionVersion(Set<Function> functions);

    String createDeviceDefinitionAndVersion(String deviceDefinitionName, List<Device> devices);

    String createGroupVersion(String groupId, GroupVersion newGroupVersion);

    String createDeployment(String groupId, String groupVersionId);

    String createSubscriptionDefinitionAndVersion(List<Subscription> subscriptions);

    String createDefaultLoggerDefinitionAndVersion();

    DeploymentStatus waitForDeploymentStatusToChange(String groupId, String deploymentId);

    String createResourceDefinitionVersion(List<FunctionConf> functionConfs);

    Device getDevice(String thingName);

    void disassociateServiceRoleFromAccount();

    void disassociateRoleFromGroup(String groupId);

    GetGroupVersionResult getLatestGroupVersion(GroupInformation groupInformation);

    List<Function> getFunctions(GroupInformation groupInformation);

    List<Device> getDevices(GroupInformation groupInformation);

    List<Subscription> getSubscriptions(GroupInformation groupInformation);

    GetGroupCertificateAuthorityResult getGroupCa(GroupInformation groupInformation);
}
