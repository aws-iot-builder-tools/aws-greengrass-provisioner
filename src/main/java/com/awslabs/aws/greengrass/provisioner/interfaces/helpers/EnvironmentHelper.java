package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import java.util.Map;

public interface EnvironmentHelper {
    String GROUP_ID = "GROUP_ID";
    String AWS_IOT_THING_NAME = "AWS_IOT_THING_NAME";
    String AWS_IOT_THING_ARN = "AWS_IOT_THING_ARN";
    String AWS_GREENGRASS_GROUP_NAME = "AWS_GREENGRASS_GROUP_NAME";
    String REGION = "REGION";

    Map<String, String> getDefaultEnvironment(String groupId, String coreThingName, String coreThingArn, String groupName);
}