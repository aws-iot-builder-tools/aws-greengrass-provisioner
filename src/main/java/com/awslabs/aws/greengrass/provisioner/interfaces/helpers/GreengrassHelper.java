package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeploymentStatus;
import com.awslabs.aws.greengrass.provisioner.data.conf.ConnectorConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.iot.data.*;
import com.awslabs.lambda.data.FunctionAliasArn;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface GreengrassHelper {
    void associateServiceRoleToAccount(Role role);

    String createGroupIfNecessary(GreengrassGroupName greengrassGroupName);

    void associateRoleToGroup(GreengrassGroupId greengrassGroupId, Role greengrassRole);

    String createCoreDefinitionAndVersion(String coreDefinitionName, CertificateArn coreCertificateArn, ThingArn coreThingArn, boolean syncShadow);

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
     * @param functionAliasArn
     * @param lambdaFunctionConfiguration
     * @param defaultEnvironment
     * @param encodingType
     * @param pinned
     * @return
     */
    Function buildFunctionModel(FunctionAliasArn functionAliasArn, FunctionConfiguration lambdaFunctionConfiguration, Map<String, String> defaultEnvironment, EncodingType encodingType, boolean pinned);

    String createFunctionDefinitionVersion(Set<Function> functions, FunctionIsolationMode defaultFunctionIsolationMode);

    String createDeviceDefinitionAndVersion(String deviceDefinitionName, List<Device> devices);

    String createLoggerDefinitionAndVersion(List<Logger> loggers);

    String createGroupVersion(GreengrassGroupId greengrassGroupId, GroupVersion newGroupVersion);

    String createDeployment(GreengrassGroupId greengrassGroupId, String groupVersionId);

    String createSubscriptionDefinitionAndVersion(List<Subscription> subscriptions);

    String createDefaultLoggerDefinitionAndVersion();

    DeploymentStatus waitForDeploymentStatusToChange(GreengrassGroupId greengrassGroupId, String deploymentId);

    String createResourceDefinitionFromFunctionConfs(List<FunctionConf> functionConfs);

    Device getDevice(ThingName thingName);

    void disassociateServiceRoleFromAccount();

    void disassociateRoleFromGroup(GreengrassGroupId greengrassGroupId);

    Optional<String> createConnectorDefinitionVersion(List<ConnectorConf> connectors);

    FunctionIsolationMode getDefaultIsolationMode(GroupInformation groupInformation);
}
