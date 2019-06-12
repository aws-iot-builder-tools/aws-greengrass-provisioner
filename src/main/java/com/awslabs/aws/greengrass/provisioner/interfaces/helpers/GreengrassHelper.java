package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ModifiableFunctionConf;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface GreengrassHelper {
    void associateServiceRoleToAccount(Role role);

    Optional<GroupInformation> getGroupInformation(String groupNameOrGroupId);

    String createGroupIfNecessary(String groupName);

    boolean groupExists(String groupName);

    void associateRoleToGroup(String groupId, Role greengrassRole);

    String createCoreDefinitionAndVersion(String coreDefinitionName, String coreCertificateArn, String coreThingArn);

    /**
     * Build a Function object for a new function
     *
     * @param functionArn
     * @param functionConf
     * @return
     */
    Function buildFunctionModel(String functionArn, FunctionConf functionConf);

    /**
     * Build a Function object for a function that exists in AWS Lambda already
     *
     * @param functionArn
     * @param lambdaFunctionConfiguration
     * @param defaultEnvironment
     * @param encodingType
     * @param pinned
     * @return
     */
    Function buildFunctionModel(String functionArn, FunctionConfiguration lambdaFunctionConfiguration, Map<String, String> defaultEnvironment, EncodingType encodingType, boolean pinned);

    String createFunctionDefinitionVersion(Set<Function> functions, FunctionIsolationMode defaultFunctionIsolationMode);

    String createDeviceDefinitionAndVersion(String deviceDefinitionName, List<Device> devices);

    String createGroupVersion(String groupId, GroupVersion newGroupVersion);

    String createDeployment(String groupId, String groupVersionId);

    String createSubscriptionDefinitionAndVersion(List<Subscription> subscriptions);

    String createDefaultLoggerDefinitionAndVersion();

    DeploymentStatus waitForDeploymentStatusToChange(String groupId, String deploymentId);

    String createResourceDefinitionVersion(List<ModifiableFunctionConf> functionConfs);

    Device getDevice(String thingName);

    void disassociateServiceRoleFromAccount();

    void disassociateRoleFromGroup(String groupId);

    GetGroupVersionResponse getLatestGroupVersion(GroupInformation groupInformation);

    List<Function> getFunctions(GroupInformation groupInformation);

    FunctionIsolationMode getDefaultIsolationMode(GroupInformation groupInformation);

    FunctionDefinitionVersion getFunctionDefinitionVersion(GroupInformation groupInformation);

    List<Device> getDevices(GroupInformation groupInformation);

    List<Subscription> getSubscriptions(GroupInformation groupInformation);

    GetGroupCertificateAuthorityResponse getGroupCa(GroupInformation groupInformation);
}
