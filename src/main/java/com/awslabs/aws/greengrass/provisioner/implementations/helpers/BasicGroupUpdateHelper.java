package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.aws.greengrass.provisioner.data.arguments.UpdateArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.iot.data.*;
import com.awslabs.iot.helpers.interfaces.V2GreengrassHelper;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import com.awslabs.lambda.data.*;
import com.awslabs.lambda.helpers.interfaces.V2LambdaHelper;
import com.google.common.collect.ImmutableSet;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.*;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BasicGroupUpdateHelper implements GroupUpdateHelper {
    private static final String SUBSCRIPTION_ERROR = "is not valid for a subscription (cloud, function ARN, or thing ARN).  When specifying a function make sure you specify the alias (e.g. \"FUNCTION:PROD\")";
    private final Logger log = LoggerFactory.getLogger(BasicGroupUpdateHelper.class);
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    V2GreengrassHelper v2GreengrassHelper;
    @Inject
    SubscriptionHelper subscriptionHelper;
    @Inject
    DeploymentHelper deploymentHelper;
    @Inject
    IotHelper iotHelper;
    @Inject
    V2IotHelper v2IotHelper;
    @Inject
    PolicyHelper policyHelper;
    @Inject
    GGVariables ggVariables;
    @Inject
    IoHelper ioHelper;
    @Inject
    LambdaHelper lambdaHelper;
    @Inject
    V2LambdaHelper v2LambdaHelper;
    @Inject
    EnvironmentHelper environmentHelper;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    UpdateArgumentHelper updateArgumentHelper;

    @Inject
    public BasicGroupUpdateHelper() {
    }

    @Override
    public void execute(UpdateArguments updateArguments) {
        if (!updateArguments.addSubscription &&
                !updateArguments.removeSubscription &&
                (updateArguments.addDevice == null) &&
                (updateArguments.removeDevice == null) &&
                (updateArguments.addFunction == null) &&
                (updateArguments.removeFunction == null)) {
            throw new RuntimeException("No update specified");
        }

        GreengrassGroupName greengrassGroupName = ImmutableGreengrassGroupName.builder().groupName(updateArguments.groupName).build();
        Optional<GroupInformation> optionalGroupInformation = v2GreengrassHelper.getGroupInformation(greengrassGroupName).findFirst();

        if (!optionalGroupInformation.isPresent()) {
            throw new RuntimeException(String.join("", "Group [", updateArguments.groupName, "] not found"));
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

        throw new RuntimeException("This should never happen.  This is a bug.");
    }

    @Override
    public ArgumentHelper<UpdateArguments> getArgumentHelper() {
        return updateArgumentHelper;
    }

    @Override
    public UpdateArguments getArguments() {
        return new UpdateArguments();
    }

    private void removeDevice(UpdateArguments updateArguments, GroupInformation groupInformation) {
        GreengrassGroupName greengrassGroupName = ImmutableGreengrassGroupName.builder().groupName(updateArguments.groupName).build();
        String deviceName = updateArguments.removeDevice;
        String groupId = groupInformation.id();

        List<Device> devices = v2GreengrassHelper.getDevices(groupInformation)
                .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

        Device deviceToRemove = greengrassHelper.getDevice(ImmutableThingName.builder().name(deviceName).build());
        String thingArn = deviceToRemove.thingArn();

        if (devices.stream().noneMatch(device -> device.thingArn().equals(deviceToRemove.thingArn()))) {
            throw new RuntimeException(String.join("", "Device with thing ARN [", thingArn, "] is not part of this Greengrass Group, nothing to do"));
        }

        List<Device> devicesToRemove = devices.stream()
                .filter(device -> device.thingArn().equals(deviceToRemove.thingArn()))
                .collect(Collectors.toList());

        for (Device device : devicesToRemove) {
            log.warn(String.join("", "Removing device [", device.thingArn(), ", ", device.certificateArn(), "]"));
        }

        devices.removeAll(devicesToRemove);

        List<Subscription> subscriptions = removeSubscriptions(groupInformation, thingArn);

        String newDeviceDefinitionVersionArn = greengrassHelper.createDeviceDefinitionAndVersion(ggVariables.getDeviceDefinitionName(greengrassGroupName), devices);
        String newSubscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        GroupVersion newGroupVersion = GroupVersion.builder()
                .deviceDefinitionVersionArn(newDeviceDefinitionVersionArn)
                .subscriptionDefinitionVersionArn(newSubscriptionDefinitionVersionArn)
                .build();

        GreengrassGroupId greengrassGroupId = ImmutableGreengrassGroupId.builder().groupId(groupId).build();
        createAndWaitForDeployment(greengrassGroupId, newGroupVersion);
    }

    private void addDevice(UpdateArguments updateArguments, GroupInformation groupInformation) {
        GreengrassGroupName greengrassGroupName = ImmutableGreengrassGroupName.builder().groupName(updateArguments.groupName).build();
        String addDeviceString = updateArguments.addDevice;
        String groupId = groupInformation.id();
        GreengrassGroupId greengrassGroupId = ImmutableGreengrassGroupId.builder().groupId(groupId).build();
        ThingArn thingArn = null;
        ThingName thingName = null;

        boolean isThingArn = addDeviceString.contains("/");

        if (!isThingArn) {
            thingName = ImmutableThingName.builder().name(addDeviceString).build();
            log.info(String.join("", "No thing ARN specified for device [", thingName.getName(), "], will re-use keys if possible"));

            KeysAndCertificate deviceKeysAndCertificate = iotHelper.createKeysAndCertificate(greengrassGroupName, thingName.getName());

            ImmutablePolicyName ggdPolicyName = ImmutablePolicyName.builder().name(String.join("_", thingName.getName(), "Policy")).build();
            CertificateArn certificateArn = deviceKeysAndCertificate.getCertificateArn();
            thingArn = v2IotHelper.createThing(thingName);

            v2IotHelper.createPolicyIfNecessary(ggdPolicyName,
                    ImmutablePolicyDocument.builder().document(policyHelper.buildDevicePolicyDocument(thingArn)).build());
            v2IotHelper.attachPrincipalPolicy(ggdPolicyName, certificateArn);
            v2IotHelper.attachThingPrincipal(thingName, certificateArn);
        } else {
            // Device name looks like a thing ARN
            log.info(String.join("", "[", addDeviceString, "] looks like a thing ARN, attempting to use existing device"));
            thingArn = ImmutableThingArn.builder().arn(addDeviceString).build();
            thingName = ImmutableThingName.builder().name(thingArn.getArn().substring(thingArn.getArn().lastIndexOf('/') + 1)).build();
            log.info(String.join("", "Device name appears to be [", thingName.getName(), "]"));
        }

        Device newDevice = greengrassHelper.getDevice(thingName);

        List<Device> devices = v2GreengrassHelper.getDevices(groupInformation)
                .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

        ThingArn finalThingArn = thingArn;

        if (devices.stream()
                .anyMatch(device -> device.thingArn().equals(finalThingArn.getArn()))) {
            throw new RuntimeException(String.join("", "Device with thing ARN [", thingArn.getArn(), "] is already part of this Greengrass Group.  Nothing to do."));
        }

        devices.add(newDevice);

        String newDeviceDefinitionVersionArn = greengrassHelper.createDeviceDefinitionAndVersion(ggVariables.getDeviceDefinitionName(greengrassGroupName), devices);

        GroupVersion newGroupVersion = GroupVersion.builder()
                .deviceDefinitionVersionArn(newDeviceDefinitionVersionArn)
                .build();

        Consumer<? super Void> successHandler = (Consumer<Void>) aVoid -> log.info(String.join("", "Device added [", newDevice.thingArn(), ", ", newDevice.certificateArn(), "]"));

        createAndWaitForDeployment(greengrassGroupId, newGroupVersion, successHandler);
    }

    private void createAndWaitForDeployment(GreengrassGroupId greengrassGroupId, GroupVersion newGroupVersion) {
        createAndWaitForDeployment(greengrassGroupId, newGroupVersion, null);
    }

    private void createAndWaitForDeployment(GreengrassGroupId greengrassGroupId, GroupVersion newGroupVersion, Consumer<? super Void> successHandler) {
        String groupVersionId = greengrassHelper.createGroupVersion(greengrassGroupId, newGroupVersion);

        if (successHandler == null) {
            successHandler = (Consumer<Void>) aVoid -> {
            };
        }

        Try.run(() -> deploymentHelper.createAndWaitForDeployment(Optional.empty(), Optional.empty(), greengrassGroupId, groupVersionId))
                .onSuccess(successHandler)
                .get();
    }

    private void addFunction(UpdateArguments updateArguments, GroupInformation groupInformation) {
        GreengrassGroupId greengrassGroupId = ImmutableGreengrassGroupId.builder().groupId(groupInformation.id()).build();
        GreengrassGroupName groupName = ImmutableGreengrassGroupName.builder().groupName(updateArguments.groupName).build();
        ThingName coreThingName = ggVariables.getCoreThingName(groupName);
        FunctionName functionName = ImmutableFunctionName.builder().name(updateArguments.addFunction).build();
        ThingArn coreThingArn = v2IotHelper.createThing(coreThingName);
        FunctionAlias functionAlias = ImmutableFunctionAlias.builder().alias(updateArguments.functionAlias).build();

        if (v2LambdaHelper.aliasExists(functionName, functionAlias)) {
            throw new RuntimeException(String.join("", "The specified alias [", functionAlias.getAlias(), "] already exists.  You must specify a new alias for an ad-hoc add function so other group configurations are not affected."));
        }

        Optional<GetFunctionResponse> optionalGetFunctionResponse = v2LambdaHelper.getFunction(functionName);

        if (!optionalGetFunctionResponse.isPresent()) {
            throw new RuntimeException(String.join("", "Function [", functionName.getName(), "] not found.  Make sure only the function name is specified without an alias or version number."));
        }

        GetFunctionResponse getFunctionResponse = optionalGetFunctionResponse.get();

        PublishVersionResponse publishVersionResponse = v2LambdaHelper.publishFunctionVersion(functionName);

        FunctionVersion functionVersion = ImmutableFunctionVersion.builder().version(publishVersionResponse.version()).build();
        FunctionAliasArn functionAliasArn = v2LambdaHelper.createAlias(functionName, functionVersion, functionAlias);

        Map<String, String> defaultEnvironment = environmentHelper.getDefaultEnvironment(greengrassGroupId, coreThingName, coreThingArn, groupName);

        Function newFunction = greengrassHelper.buildFunctionModel(functionAliasArn,
                getFunctionResponse.configuration(),
                defaultEnvironment,
                updateArguments.functionBinary ? EncodingType.BINARY : EncodingType.JSON,
                updateArguments.functionPinned);

        List<Function> functions = v2GreengrassHelper.getFunctions(groupInformation)
                .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

        if (functions.stream()
                .anyMatch(function -> function.functionArn().equals(functionAliasArn))) {
            throw new RuntimeException(String.join("", "Function with ARN [", functionAliasArn.getAliasArn(), "] is already part of this Greengrass Group.  Nothing to do."));
        }

        functions.add(newFunction);

        String newFunctionDefinitionVersionArn = greengrassHelper.createFunctionDefinitionVersion(ImmutableSet.copyOf(functions), greengrassHelper.getDefaultIsolationMode(groupInformation));

        GroupVersion newGroupVersion = GroupVersion.builder()
                .functionDefinitionVersionArn(newFunctionDefinitionVersionArn)
                .build();

        Consumer<? super Void> successHandler = (Consumer<Void>) aVoid -> log.info(String.join("", "Function added [", newFunction.functionArn(), "]"));

        createAndWaitForDeployment(greengrassGroupId, newGroupVersion, successHandler);
    }

    private void removeFunction(UpdateArguments updateArguments, GroupInformation groupInformation) {
        GreengrassGroupId greengrassGroupId = ImmutableGreengrassGroupId.builder().groupId(groupInformation.id()).build();
        List<Function> functions = v2GreengrassHelper.getFunctions(groupInformation)
                .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

        String functionArn = String.join(":", updateArguments.removeFunction, updateArguments.functionAlias);

        List<Function> functionsToDelete = functions.stream()
                .filter(function -> function.functionArn().endsWith(functionArn))
                .collect(Collectors.toList());

        if (functionsToDelete.size() == 0) {
            throw new RuntimeException(String.join("", "Function with ARN [", functionArn, "] is not part of this Greengrass Group.  Nothing to do."));
        } else if (functionsToDelete.size() > 1) {
            throw new RuntimeException(String.join("", "More than one function matched the pattern [", functionArn, "].  Only one function can be removed at a time."));
        }

        Function functionToDelete = functionsToDelete.get(0);

        String functionToDeleteArn = functionToDelete.functionArn();

        functions.remove(functionToDelete);

        List<Subscription> subscriptions = removeSubscriptions(groupInformation, functionToDeleteArn);
        String newSubscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        String newFunctionDefinitionVersionArn = greengrassHelper.createFunctionDefinitionVersion(ImmutableSet.copyOf(functions), greengrassHelper.getDefaultIsolationMode(groupInformation));

        GroupVersion newGroupVersion = GroupVersion.builder()
                .subscriptionDefinitionVersionArn(newSubscriptionDefinitionVersionArn)
                .functionDefinitionVersionArn(newFunctionDefinitionVersionArn)
                .build();

        createAndWaitForDeployment(greengrassGroupId, newGroupVersion, nothing -> logFunctionRemovedAndDeleteLambdaAlias(functionToDelete, functionToDeleteArn));
    }

    private void logFunctionRemovedAndDeleteLambdaAlias(Function functionToDelete, String functionToDeleteArn) {
        log.info(String.join("", "Function removed [", functionToDelete.functionArn(), "]"));
        lambdaHelper.deleteAlias(functionToDeleteArn);
    }

    private List<Subscription> removeSubscriptions(GroupInformation groupInformation, String thingOrFunctionArn) {
        List<Subscription> subscriptions = v2GreengrassHelper.getSubscriptions(groupInformation)
                .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

        Set<Subscription> subscriptionsToRemove = getMatchingSubscriptions(subscriptions, thingOrFunctionArn, true, Optional.empty());

        for (Subscription subscription : subscriptionsToRemove) {
            log.warn(String.join("", "Removing subscription [", subscription.source(), ", ", subscription.target(), ", ", subscription.subject(), "]"));
        }

        subscriptions.removeAll(subscriptionsToRemove);
        return subscriptions;
    }

    private void addOrRemoveSubscription(UpdateArguments updateArguments, GroupInformation groupInformation) {
        List<Subscription> subscriptions = v2GreengrassHelper.getSubscriptions(groupInformation)
                .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

        String source = updateArguments.subscriptionSource;
        String target = updateArguments.subscriptionTarget;
        String subject = updateArguments.subscriptionSubject;
        GreengrassGroupId greengrassGroupId = ImmutableGreengrassGroupId.builder().groupId(groupInformation.id()).build();

        if (updateArguments.addSubscription) {
            List<Function> functions = v2GreengrassHelper.getFunctions(groupInformation)
                    .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));
            List<Device> devices = v2GreengrassHelper.getDevices(groupInformation)
                    .orElseThrow(() -> new RuntimeException("Group not found, can not continue"));

            // Is the source valid?
            Optional<String> validatedSource = validateSubscriptionSourceOrTarget(source, functions, devices);

            if (!validatedSource.isPresent()) {
                // Source is invalid
                throw new RuntimeException(String.join("", "Source [", source, "] ", SUBSCRIPTION_ERROR));
            } else {
                source = validatedSource.get();
            }

            // Is the target valid?
            Optional<String> validatedTarget = validateSubscriptionSourceOrTarget(target, functions, devices);

            if (!validatedTarget.isPresent()) {
                // Target is invalid
                throw new RuntimeException(String.join("", "Target [", target, "] ", SUBSCRIPTION_ERROR));
            } else {
                target = validatedTarget.get();
            }

            Set<Subscription> existingSubscriptions = getMatchingSubscriptions(subscriptions, source, target, subject);

            if (existingSubscriptions.size() != 0) {
                throw new RuntimeException("Subscription already exists.  Nothing to do.");
            }

            Subscription subscription = subscriptionHelper.createSubscription(source, target, subject);
            subscriptions.add(subscription);

            log.info(String.join("", "Subscription added [", subscription.source(), ", ", subscription.target(), ", ", subscription.subject(), "]"));
        } else if (updateArguments.removeSubscription) {
            Set<Subscription> existingSubscriptions = getMatchingSubscriptions(subscriptions, source, target, subject);

            if (existingSubscriptions.size() == 0) {
                throw new RuntimeException("Subscription doesn't exist.  Nothing to do.");
            } else if (existingSubscriptions.size() > 1) {
                log.error(jsonHelper.toJson(existingSubscriptions));
                throw new RuntimeException("More than one matching subscription exists.  Specify a more specific match.");
            }

            // Exactly one, remove it
            Subscription subscriptionToRemove = existingSubscriptions.iterator().next();
            subscriptions.remove(subscriptionToRemove);
            log.info(String.join("", "Subscription removed [", subscriptionToRemove.source(), ", ", subscriptionToRemove.target(), ", ", subscriptionToRemove.subject(), "]"));
        } else {
            throw new RuntimeException("This should never happen.  This is a bug.");
        }

        String newSubscriptionDefinitionVersionArn = greengrassHelper.createSubscriptionDefinitionAndVersion(subscriptions);

        GroupVersion newGroupVersion = GroupVersion.builder()
                .subscriptionDefinitionVersionArn(newSubscriptionDefinitionVersionArn)
                .build();

        // Do nothing extra if successful
        createAndWaitForDeployment(greengrassGroupId, newGroupVersion);
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
                .filter(subscription -> checkTarget(target, exactTarget, subscription))
                .collect(Collectors.toList());
    }

    private boolean checkTarget(String target, boolean exactTarget, Subscription subscription) {
        if (exactTarget) {
            return subscription.target().equals(target);
        }

        return subscription.target().endsWith(target);
    }

    private List<Subscription> getSourceMatches(String source, boolean exactSource, List<Subscription> subjectMatches) {
        return subjectMatches.stream()
                .filter(subscription -> matchSource(source, exactSource, subscription))
                .collect(Collectors.toList());
    }

    private boolean matchSource(String source, boolean exactSource, Subscription subscription) {
        if (exactSource) {
            return subscription.source().equals(source);
        }

        return subscription.source().endsWith(source);
    }

    private List<Subscription> getSubjectMatches(List<Subscription> subscriptions, Optional<String> optionalSubject) {
        return subscriptions.stream()
                .filter(subscription -> checkSubject(optionalSubject, subscription))
                .collect(Collectors.toList());
    }

    private boolean checkSubject(Optional<String> optionalSubject, Subscription subscription) {
        if (optionalSubject.isPresent()) {
            return subscription.subject().equals(optionalSubject.get());
        }

        return true;
    }

    private Optional<String> validateSubscriptionSourceOrTarget(String sourceOrTarget, List<Function> functions, List<Device> devices) {
        if (sourceOrTarget.equals(subscriptionHelper.CLOUD)) {
            // Yes, it is the cloud
            return Optional.ofNullable(subscriptionHelper.CLOUD);
        }

        // Is it a function?
        List<String> functionArns = functions.stream()
                .filter(function -> function.functionArn().endsWith(sourceOrTarget))
                .map(Function::functionArn)
                .collect(Collectors.toList());

        if (functionArns.size() == 1) {
            return Optional.ofNullable(functionArns.get(0));
        } else if (functionArns.size() > 1) {
            log.error(String.join("", "Multiple functions match pattern [", sourceOrTarget, "], please specify a more specific match"));
            return Optional.empty();
        }

        // Is it a device?
        List<String> thingArns = devices.stream()
                .filter(device -> device.thingArn().endsWith(sourceOrTarget))
                .map(Device::thingArn)
                .collect(Collectors.toList());

        if (thingArns.size() == 1) {
            return Optional.ofNullable(thingArns.get(0));
        } else if (thingArns.size() > 1) {
            log.error(String.join("", "Multiple things match pattern [", sourceOrTarget, "], please specify a more specific match"));
            return Optional.empty();
        }

        return Optional.empty();
    }
}
