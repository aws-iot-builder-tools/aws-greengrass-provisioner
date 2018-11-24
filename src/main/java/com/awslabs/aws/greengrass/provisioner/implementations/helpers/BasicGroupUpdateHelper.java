package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.greengrass.model.*;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.PublishVersionResult;
import com.awslabs.aws.greengrass.provisioner.data.UpdateArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class BasicGroupUpdateHelper implements GroupUpdateHelper {
    public static final String SUBSCRIPTION_ERROR = "is not valid for a subscription (cloud, function ARN, or thing ARN).  When specifying a function make sure you specify the alias (e.g. \"FUNCTION:PROD\")";

    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    SubscriptionHelper subscriptionHelper;
    @Inject
    DeploymentHelper deploymentHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    PolicyHelper policyHelper;
    @Inject
    GGVariables ggVariables;
    @Inject
    IoHelper ioHelper;
    @Inject
    LambdaHelper lambdaHelper;
    @Inject
    EnvironmentHelper environmentHelper;
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public BasicGroupUpdateHelper() {
    }

    @Override
    public void doUpdate(UpdateArguments updateArguments) {
        if (!updateArguments.addSubscription &&
                !updateArguments.removeSubscription &&
                (updateArguments.addDevice == null) &&
                (updateArguments.removeDevice == null) &&
                (updateArguments.addFunction == null) &&
                (updateArguments.removeFunction == null)) {
            log.error("No update specified");
            return;
        }

        Optional<GroupInformation> optionalGroupInformation = greengrassHelper.getGroupInformation(updateArguments.groupName);

        if (!optionalGroupInformation.isPresent()) {
            log.error("Group [" + updateArguments.groupName + "] not found");
            return;
        }

        if (updateArguments.addSubscription || updateArguments.removeSubscription) {
            addOrRemoveSubscription(updateArguments, optionalGroupInformation.get());
            return;
        }

        if (updateArguments.addDevice != null) {
            addDevice(updateArguments, optionalGroupInformation.get());
            return;
        }

        if (updateArguments.removeDevice != null) {
            removeDevice(updateArguments, optionalGroupInformation.get());
            return;
        }

        if (updateArguments.addFunction != null) {
            addFunction(updateArguments, optionalGroupInformation.get());
            return;
        }

        if (updateArguments.removeFunction != null) {
            removeFunction(updateArguments, optionalGroupInformation.get());
            return;
        }

        throw new UnsupportedOperationException("This should never happen.  This is a bug.");
    }

    private void removeDevice(UpdateArguments updateArguments, GroupInformation groupInformation) {
        String groupName = updateArguments.groupName;
        String deviceName = updateArguments.removeDevice;
        String groupId = groupInformation.getId();

        List<Device> devices = greengrassHelper.getDevices(groupInformation);

        Device deviceToRemove = greengrassHelper.getDevice(deviceName);
        String thingArn = deviceToRemove.getThingArn();

        if (!devices.stream().anyMatch(device -> device.getThingArn().equals(deviceToRemove.getThingArn()))) {
            log.error("Device with thing ARN [" + thingArn + "] is not part of this Greengrass Group, nothing to do");
            return;
        }

        List<Device> devicesToRemove = devices.stream()
                .filter(device -> device.getThingArn().equals(deviceToRemove.getThingArn()))
                .collect(Collectors.toList());

        for (Device device : devicesToRemove) {
            log.warn("Removing device [" + device.getThingArn() + ", " + device.getCertificateArn() + "]");
        }

        devices.removeAll(devicesToRemove);

        List<Subscription> subscriptions = removeSubscriptions(groupInformation, thingArn);

        String newDeviceDefinitionVersionArn = greengrassHelper.createDeviceDefinitionAndVersion(ggVariables.getDeviceDefinitionName(groupName), devices);
        String newSubscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        GroupVersion newGroupVersion = new GroupVersion()
                .withDeviceDefinitionVersionArn(newDeviceDefinitionVersionArn)
                .withSubscriptionDefinitionVersionArn(newSubscriptionDefinitionVersionArn);

        String groupVersionId = greengrassHelper.createGroupVersion(groupId, newGroupVersion);

        deploymentHelper.createAndWaitForDeployment(Optional.empty(), Optional.empty(), groupId, groupVersionId);
    }

    private void addDevice(UpdateArguments updateArguments, GroupInformation groupInformation) {
        String groupName = updateArguments.groupName;
        String deviceName = updateArguments.addDevice;
        String groupId = groupInformation.getId();
        String thingArn = null;

        boolean isThingArn = deviceName.contains("/");

        if (!isThingArn) {
            log.info("No thing ARN specified for device [" + deviceName + "], will re-use keys if possible");

            CreateKeysAndCertificateResult deviceKeysAndCertificate = iotHelper.createOrLoadKeysAndCertificate(groupName, deviceName);

            String ggdPolicyName = String.join("_", deviceName, "Policy");
            String certificateArn = deviceKeysAndCertificate.getCertificateArn();
            thingArn = iotHelper.createThing(deviceName);

            iotHelper.createPolicyIfNecessary(ggdPolicyName, policyHelper.buildDevicePolicyDocument(thingArn));
            iotHelper.attachPrincipalPolicy(ggdPolicyName, certificateArn);
            iotHelper.attachThingPrincipal(deviceName, certificateArn);

            String privateKeyFilename = "build/" + String.join(".", deviceName, "pem", "key");
            String publicSignedCertificateFilename = "build/" + String.join(".", deviceName, "pem", "crt");

            ioHelper.writeFile(privateKeyFilename, deviceKeysAndCertificate.getKeyPair().getPrivateKey().getBytes());
            log.info("Device private key written to [" + privateKeyFilename + "]");
            ioHelper.writeFile(publicSignedCertificateFilename, deviceKeysAndCertificate.getCertificatePem().getBytes());
            log.info("Device public signed certificate key written to [" + publicSignedCertificateFilename + "]");
        } else {
            // Device name looks like a thing ARN
            log.info("[" + deviceName + "] looks like a thing ARN, attempting to use existing device");
            thingArn = deviceName;
            deviceName = thingArn.substring(thingArn.lastIndexOf('/') + 1);
            log.info("Device name appears to be [" + deviceName + "]");
        }

        Device newDevice = greengrassHelper.getDevice(deviceName);

        List<Device> devices = greengrassHelper.getDevices(groupInformation);

        String finalThingArn = thingArn;

        if (devices.stream()
                .anyMatch(device -> device.getThingArn().equals(finalThingArn))) {
            log.error("Device with thing ARN [" + thingArn + "] is already part of this Greengrass Group.  Nothing to do.");
            return;
        }

        devices.add(newDevice);

        String newDeviceDefinitionVersionArn = greengrassHelper.createDeviceDefinitionAndVersion(ggVariables.getDeviceDefinitionName(groupName), devices);


        GroupVersion newGroupVersion = new GroupVersion()
                .withDeviceDefinitionVersionArn(newDeviceDefinitionVersionArn);

        String groupVersionId = greengrassHelper.createGroupVersion(groupId, newGroupVersion);

        if (deploymentHelper.createAndWaitForDeployment(Optional.empty(), Optional.empty(), groupId, groupVersionId)) {
            log.info("Device added [" + newDevice.getThingArn() + ", " + newDevice.getCertificateArn() + "]");
        }
    }

    private void addFunction(UpdateArguments updateArguments, GroupInformation groupInformation) {
        String groupId = groupInformation.getId();
        String groupName = updateArguments.groupName;
        String coreThingName = ggVariables.getCoreThingName(groupName);
        String functionName = updateArguments.addFunction;
        String coreThingArn = iotHelper.createThing(coreThingName);
        String functionAlias = updateArguments.functionAlias;

        if (lambdaHelper.aliasExists(functionName, functionAlias)) {
            log.error("The specified alias [" + functionAlias + "] already exists.  You must specify a new alias for an ad-hoc add function so other group configurations are not affected.");
            return;
        }

        Optional<GetFunctionResult> optionalGetFunctionResult = lambdaHelper.getFunction(functionName);

        if (!optionalGetFunctionResult.isPresent()) {
            log.error("Function [" + functionName + "] not found.  Make sure only the function name is specified without an alias or version number.");
            return;
        }

        GetFunctionResult getFunctionResult = optionalGetFunctionResult.get();

        PublishVersionResult publishVersionResult = lambdaHelper.publishFunctionVersion(functionName);

        String aliasArn = lambdaHelper.createAlias(Optional.empty(), functionName, publishVersionResult.getVersion(), functionAlias);

        Map<String, String> defaultEnvironment = environmentHelper.getDefaultEnvironment(groupId, coreThingName, coreThingArn, groupName);

        Function newFunction = greengrassHelper.buildFunctionModel(aliasArn,
                getFunctionResult.getConfiguration(),
                defaultEnvironment,
                updateArguments.functionBinary ? EncodingType.Binary : EncodingType.Json,
                updateArguments.functionPinned);

        List<Function> functions = greengrassHelper.getFunctions(groupInformation);

        if (functions.stream()
                .anyMatch(function -> function.getFunctionArn().equals(aliasArn))) {
            log.error("Function with ARN [" + aliasArn + "] is already part of this Greengrass Group.  Nothing to do.");
            return;
        }

        functions.add(newFunction);

        String newFunctionDefinitionVersionArn = greengrassHelper.createFunctionDefinitionVersion(ImmutableSet.copyOf(functions));

        GroupVersion newGroupVersion = new GroupVersion()
                .withFunctionDefinitionVersionArn(newFunctionDefinitionVersionArn);

        String groupVersionId = greengrassHelper.createGroupVersion(groupId, newGroupVersion);

        if (deploymentHelper.createAndWaitForDeployment(Optional.empty(), Optional.empty(), groupId, groupVersionId)) {
            log.info("Function added [" + newFunction.getFunctionArn() + "]");
        }
    }

    private void removeFunction(UpdateArguments updateArguments, GroupInformation groupInformation) {
        String groupId = groupInformation.getId();
        List<Function> functions = greengrassHelper.getFunctions(groupInformation);

        String functionArn = String.join(":", updateArguments.removeFunction, updateArguments.functionAlias);

        List<Function> functionsToDelete = functions.stream()
                .filter(function -> function.getFunctionArn().endsWith(functionArn))
                .collect(Collectors.toList());

        if (functionsToDelete.size() == 0) {
            log.error("Function with ARN [" + functionArn + "] is not part of this Greengrass Group.  Nothing to do.");
            return;
        } else if (functionsToDelete.size() > 1) {
            log.error("More than one function matched the pattern [" + functionArn + "].  Only one function can be removed at a time.");
            return;
        }

        Function functionToDelete = functionsToDelete.get(0);

        String functionToDeleteArn = functionToDelete.getFunctionArn();

        functions.remove(functionToDelete);

        List<Subscription> subscriptions = removeSubscriptions(groupInformation, functionToDeleteArn);
        String newSubscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        String newFunctionDefinitionVersionArn = greengrassHelper.createFunctionDefinitionVersion(ImmutableSet.copyOf(functions));

        GroupVersion newGroupVersion = new GroupVersion()
                .withSubscriptionDefinitionVersionArn(newSubscriptionDefinitionVersionArn)
                .withFunctionDefinitionVersionArn(newFunctionDefinitionVersionArn);

        String groupVersionId = greengrassHelper.createGroupVersion(groupId, newGroupVersion);

        if (deploymentHelper.createAndWaitForDeployment(Optional.empty(), Optional.empty(), groupId, groupVersionId)) {
            log.info("Function removed [" + functionToDelete.getFunctionArn() + "]");
            lambdaHelper.deleteAlias(functionToDeleteArn);
        }
    }

    private List<Subscription> removeSubscriptions(GroupInformation groupInformation, String thingOrFunctionArn) {
        List<Subscription> subscriptions = greengrassHelper.getSubscriptions(groupInformation);

        Set<Subscription> subscriptionsToRemove = getMatchingSubscriptions(subscriptions, thingOrFunctionArn, true, Optional.empty());

        for (Subscription subscription : subscriptionsToRemove) {
            log.warn("Removing subscription [" + subscription.getSource() + ", " + subscription.getTarget() + ", " + subscription.getSubject() + "]");
        }

        subscriptions.removeAll(subscriptionsToRemove);
        return subscriptions;
    }

    private void addOrRemoveSubscription(UpdateArguments updateArguments, GroupInformation groupInformation) {
        List<Subscription> subscriptions = greengrassHelper.getSubscriptions(groupInformation);

        String source = updateArguments.subscriptionSource;
        String target = updateArguments.subscriptionTarget;
        String subject = updateArguments.subscriptionSubject;
        String groupId = groupInformation.getId();

        if (updateArguments.addSubscription) {
            List<Function> functions = greengrassHelper.getFunctions(groupInformation);
            List<Device> devices = greengrassHelper.getDevices(groupInformation);

            // Is the source valid?
            Optional<String> validatedSource = validateSubscriptionSourceOrTarget(source, functions, devices);

            if (!validatedSource.isPresent()) {
                // Source is invalid
                log.error("Source [" + source + "] " + SUBSCRIPTION_ERROR);
                return;
            } else {
                source = validatedSource.get();
            }

            // Is the target valid?
            Optional<String> validatedTarget = validateSubscriptionSourceOrTarget(target, functions, devices);

            if (!validatedTarget.isPresent()) {
                // Target is invalid
                log.error("Target [" + target + "] " + SUBSCRIPTION_ERROR);
                return;
            } else {
                target = validatedTarget.get();
            }

            Set<Subscription> existingSubscriptions = getMatchingSubscriptions(subscriptions, source, target, subject);

            if (existingSubscriptions.size() != 0) {
                log.error("Subscription already exists.  Nothing to do.");
                return;
            }

            Subscription subscription = subscriptionHelper.createSubscription(source, target, subject);
            subscriptions.add(subscription);

            log.info("Subscription added [" + subscription.getSource() + ", " + subscription.getTarget() + ", " + subscription.getSubject() + "]");
        } else if (updateArguments.removeSubscription) {
            Set<Subscription> existingSubscriptions = getMatchingSubscriptions(subscriptions, source, target, subject);

            if (existingSubscriptions.size() == 0) {
                log.error("Subscription doesn't exist.  Nothing to do.");
                return;
            } else if (existingSubscriptions.size() > 1) {
                log.error(jsonHelper.toJson(existingSubscriptions));
                log.error("More than one matching subscription exists.  Specify a more specific match.");
                return;
            }

            // Exactly one, remove it
            Subscription subscriptionToRemove = existingSubscriptions.iterator().next();
            subscriptions.remove(subscriptionToRemove);
            log.info("Subscription removed [" + subscriptionToRemove.getSource() + ", " + subscriptionToRemove.getTarget() + ", " + subscriptionToRemove.getSubject() + "]");
        } else {
            throw new UnsupportedOperationException("This should never happen.  This is a bug.");
        }

        String newSubscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        GroupVersion newGroupVersion = new GroupVersion()
                .withSubscriptionDefinitionVersionArn(newSubscriptionDefinitionVersionArn);

        String groupVersionId = greengrassHelper.createGroupVersion(groupId, newGroupVersion);

        deploymentHelper.createAndWaitForDeployment(Optional.empty(), Optional.empty(), groupId, groupVersionId);
    }

    /**
     * Returns matching subscriptions from a list of subscriptions.  Source and target must only end with the specified values.
     *
     * @param subscriptions
     * @param source
     * @param target
     * @param subject
     * @return
     */
    private Set<Subscription> getMatchingSubscriptions(List<Subscription> subscriptions, String source, String target, String subject) {
        return getMatchingSubscriptions(subscriptions, source, false, target, false, Optional.ofNullable(subject));
    }

    /**
     * Returns matching subscriptions from a list of subscriptions if the specified values match the source AND the target
     *
     * @param subscriptions   the list of subscriptions
     * @param source          the source string
     * @param exactSource     if true, an exact match of the source is required. If false, the subscription's source value must end with the specified source value.
     * @param target          the target string
     * @param exactTarget     if true, an exact match of the target is required. If false, the subscription's source value must end with the specified target value.
     * @param optionalSubject if empty, the subject is not checked. Otherwise the subject must match exactly.
     * @return
     */
    private Set<Subscription> getMatchingSubscriptions(List<Subscription> subscriptions, String source, boolean exactSource, String target, boolean exactTarget, Optional<String> optionalSubject) {
        List<Subscription> subjectMatches = getSubjectMatches(subscriptions, optionalSubject);

        List<Subscription> sourceMatches = getSourceMatches(source, exactSource, subjectMatches);

        List<Subscription> targetMatches = getTargetMatches(target, exactTarget, sourceMatches);

        Set<Subscription> matchingSubscriptions = new HashSet<>();
        matchingSubscriptions.addAll(targetMatches);

        return matchingSubscriptions;
    }

    /**
     * Returns matching subscriptions from a list of subscriptions if the specified value matches the source OR the target
     *
     * @param subscriptions       the list of subscriptions
     * @param sourceOrTarget      the source or target string
     * @param exactSourceOrTarget if true, an exact match of the source or target is required. If false, the subscription's source or target value must end with the specified source or target value.
     * @param optionalSubject     if empty, the subject is not checked. Otherwise the subject must match exactly.
     * @return
     */
    private Set<Subscription> getMatchingSubscriptions(List<Subscription> subscriptions, String sourceOrTarget, boolean exactSourceOrTarget, Optional<String> optionalSubject) {
        List<Subscription> subjectMatches = getSubjectMatches(subscriptions, optionalSubject);

        List<Subscription> sourceMatches = getSourceMatches(sourceOrTarget, exactSourceOrTarget, subjectMatches);

        List<Subscription> targetMatches = getTargetMatches(sourceOrTarget, exactSourceOrTarget, subjectMatches);

        Set<Subscription> matchingSubscriptions = new HashSet<>();
        matchingSubscriptions.addAll(sourceMatches);
        matchingSubscriptions.addAll(targetMatches);

        return matchingSubscriptions;
    }

    private List<Subscription> getTargetMatches(String target, boolean exactTarget, List<Subscription> sourceMatches) {
        return sourceMatches.stream()
                .filter(subscription -> {
                    if (exactTarget) {
                        return subscription.getTarget().equals(target);
                    }

                    return subscription.getTarget().endsWith(target);
                })
                .collect(Collectors.toList());
    }

    private List<Subscription> getSourceMatches(String source, boolean exactSource, List<Subscription> subjectMatches) {
        return subjectMatches.stream()
                .filter(subscription -> {
                    if (exactSource) {
                        return subscription.getSource().equals(source);
                    }

                    return subscription.getSource().endsWith(source);
                })
                .collect(Collectors.toList());
    }

    private List<Subscription> getSubjectMatches(List<Subscription> subscriptions, Optional<String> optionalSubject) {
        return subscriptions.stream()
                .filter(subscription -> {
                    if (optionalSubject.isPresent()) {
                        return subscription.getSubject().equals(optionalSubject.get());
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    private Optional<String> validateSubscriptionSourceOrTarget(String sourceOrTarget, List<Function> functions, List<Device> devices) {
        if (sourceOrTarget.equals(subscriptionHelper.CLOUD)) {
            // Yes, it is the cloud
            return Optional.ofNullable(subscriptionHelper.CLOUD);
        }

        // Is it a function?
        List<String> functionArns = functions.stream()
                .filter(function -> function.getFunctionArn().endsWith(sourceOrTarget))
                .map(Function::getFunctionArn)
                .collect(Collectors.toList());

        if (functionArns.size() == 1) {
            return Optional.ofNullable(functionArns.get(0));
        } else if (functionArns.size() > 1) {
            log.error("Multiple functions match pattern [" + sourceOrTarget + "], please specify a more specific match");
            return Optional.empty();
        }

        // Is it a device?
        List<String> thingArns = devices.stream()
                .filter(device -> device.getThingArn().endsWith(sourceOrTarget))
                .map(Device::getThingArn)
                .collect(Collectors.toList());

        if (thingArns.size() == 1) {
            return Optional.ofNullable(thingArns.get(0));
        } else if (thingArns.size() > 1) {
            log.error("Multiple things match pattern [" + sourceOrTarget + "], please specify a more specific match");
            return Optional.empty();
        }

        return Optional.empty();
    }
}
