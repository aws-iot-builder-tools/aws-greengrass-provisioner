package com.awslabs.aws.greengrass.provisioner.data.arguments;

import com.beust.jcommander.Parameter;

public class QueryArguments extends Arguments {
    private final String LONG_QUERY_GROUP_OPTION = "--query-group";
    private final String LONG_GET_GROUP_CA_OPTION = "--get-group-ca";
    private final String LONG_LIST_SUBSCRIPTIONS_OPTION = "--list-subscriptions";
    private final String LONG_LIST_FUNCTIONS_OPTION = "--list-functions";
    private final String LONG_LIST_DEVICES_OPTION = "--list-devices";
    private final String LONG_WRITE_TO_FILE_OPTION = "--write-to-file";
    private final String LONG_DOWNLOAD_LOGS = "--download-logs";
    private final String LONG_WATCH_LOGS = "--watch-logs";
    private final String LONG_DIAGNOSE = "--diagnose";
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
    @Parameter(names = {LONG_DOWNLOAD_LOGS}, description = "Download the group's CloudWatch logs")
    public boolean downloadLogs;
    @Parameter(names = {LONG_DIAGNOSE}, description = "Diagnose Greengrass issues")
    public boolean diagnose;
    @Parameter(names = {LONG_WATCH_LOGS}, description = "Watch the group's CloudWatch logs")
    public boolean watchLogs;
    @Parameter(names = "--help", help = true)
    private boolean help;

    @Override
    public boolean isHelp() {
        return help;
    }

    @Override
    public String getRequiredOptionName() {
        return LONG_QUERY_GROUP_OPTION;
    }

    @Override
    public boolean isRequiredOptionSet() {
        return queryGroup;
    }
}
