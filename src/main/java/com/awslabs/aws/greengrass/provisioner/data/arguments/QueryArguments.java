package com.awslabs.aws.greengrass.provisioner.data.arguments;

import com.beust.jcommander.Parameter;
import lombok.Getter;

public class QueryArguments extends Arguments {
    private final String LONG_QUERY_GROUP_OPTION = "--query-group";
    @Getter
    private final String requiredOptionName = LONG_QUERY_GROUP_OPTION;
    private final String LONG_GET_GROUP_CA_OPTION = "--get-group-ca";
    private final String LONG_LIST_SUBSCRIPTIONS_OPTION = "--list-subscriptions";
    private final String LONG_LIST_FUNCTIONS_OPTION = "--list-functions";
    private final String LONG_LIST_DEVICES_OPTION = "--list-devices";
    private final String LONG_WRITE_TO_FILE_OPTION = "--write-to-file";
    @Parameter(names = {LONG_QUERY_GROUP_OPTION}, description = "Query an existing Greengrass group (must specify additional options)")
    public boolean queryGroup;
    @Parameter(names = {LONG_GROUP_NAME_OPTION, SHORT_GROUP_NAME_OPTION}, description = "The name of the Greengrass group")
    public String groupName;
    @Parameter(names = {LONG_GET_GROUP_CA_OPTION}, description = "Get the group CA")
    public boolean getGroupCa;
    @Parameter(names = {LONG_LIST_SUBSCRIPTIONS_OPTION}, description = "List the subscriptions")
    public boolean listSubscriptions;
    @Parameter(names = {LONG_LIST_FUNCTIONS_OPTION}, description = "List the functions")
    public boolean listFunctions;
    @Parameter(names = {LONG_LIST_DEVICES_OPTION}, description = "List the devices")
    public boolean listDevices;
    @Parameter(names = {LONG_WRITE_TO_FILE_OPTION}, description = "(Optional) Whether or not to write the output to a file")
    public boolean writeToFile;
    @Parameter(names = "--help", help = true)
    @Getter
    public boolean help;

    @Override
    public boolean isRequiredOptionSet() {
        return queryGroup;
    }
}
