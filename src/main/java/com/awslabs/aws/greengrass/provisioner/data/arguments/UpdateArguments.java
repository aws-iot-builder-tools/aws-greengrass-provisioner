package com.awslabs.aws.greengrass.provisioner.data.arguments;

import com.beust.jcommander.Parameter;

public class UpdateArguments extends Arguments {
    private final String LONG_UPDATE_GROUP_OPTION = "--update-group";
    private final String LONG_ADD_SUBSCRIPTION_OPTION = "--add-subscription";
    private final String REMOVE_SUBSCRIPTION_OPTION = "--remove-subscription";
    private final String LONG_ADD_DEVICE_OPTION = "--add-device";
    private final String LONG_REMOVE_DEVICE_OPTION = "--remove-device";
    private final String LONG_ADD_FUNCTION_OPTION = "--add-function";
    private final String LONG_REMOVE_FUNCTION_OPTION = "--remove-function";
    private final String LONG_THING_ARN_OPTION = "--thing-arn";
    private final String LONG_FUNCTION_ALIAS_OPTION = "--function-alias";
    private final String LONG_FUNCTION_BINARY_OPTION = "--function-binary";
    private final String LONG_FUNCTION_PINNED_OPTION = "--function-pinned";
    private final String SUBSCRIPTION_SOURCE_OPTION = "--subscription-source";
    private final String SUBSCRIPTION_SUBJECT_OPTION = "--subscription-subject";
    private final String SUBSCRIPTION_TARGET_OPTION = "--subscription-target";
    @Parameter(names = {LONG_UPDATE_GROUP_OPTION}, description = "Update an existing Greengrass group (must specify additional options)")
    public boolean updateGroup;
    @Parameter(names = {LONG_GROUP_NAME_OPTION, SHORT_GROUP_NAME_OPTION}, description = "The name of the Greengrass group")
    public String groupName;
    @Parameter(names = {LONG_ADD_SUBSCRIPTION_OPTION}, description = "Adds an entry to the subscription table for an existing group")
    public boolean addSubscription;
    @Parameter(names = {REMOVE_SUBSCRIPTION_OPTION}, description = "Removes an entry from the subscription table for an existing group")
    public boolean removeSubscription;
    @Parameter(names = {LONG_ADD_DEVICE_OPTION}, description = "Adds a device to the device table for an existing group")
    public String addDevice;
    @Parameter(names = {LONG_REMOVE_DEVICE_OPTION}, description = "Removes a device from the device table for an existing group")
    public String removeDevice;
    @Parameter(names = {LONG_ADD_FUNCTION_OPTION}, description = "Adds a function to the function table for an existing group")
    public String addFunction;
    @Parameter(names = {LONG_REMOVE_FUNCTION_OPTION}, description = "Removes a function from the function table for an existing group")
    public String removeFunction;
    @Parameter(names = {LONG_FUNCTION_ALIAS_OPTION}, description = "The function alias to use")
    public String functionAlias;
    @Parameter(names = {LONG_FUNCTION_BINARY_OPTION}, description = "Whether the function expects binary payloads or not")
    public boolean functionBinary;
    @Parameter(names = {LONG_FUNCTION_PINNED_OPTION}, description = "Whether the function is pinned or not")
    public boolean functionPinned;
    @Parameter(names = {SUBSCRIPTION_SOURCE_OPTION}, description = "The source for a subscription table update")
    public String subscriptionSource;
    @Parameter(names = {SUBSCRIPTION_SUBJECT_OPTION}, description = "The subject for a subscription table update")
    public String subscriptionSubject;
    @Parameter(names = {SUBSCRIPTION_TARGET_OPTION}, description = "The target for a subscription table update")
    public String subscriptionTarget;
    @Parameter(names = "--help", help = true)
    private boolean help;

    @Override
    public String getRequiredOptionName() {
        return LONG_UPDATE_GROUP_OPTION;
    }

    @Override
    public boolean isRequiredOptionSet() {
        return updateGroup;
    }

    @Override
    public boolean isHelp() {
        return help;
    }
}
