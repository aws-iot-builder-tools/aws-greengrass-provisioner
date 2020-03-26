package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.iot.data.GreengrassGroupId;
import com.awslabs.iot.data.GreengrassGroupName;
import com.awslabs.iot.data.ThingArn;
import com.awslabs.iot.data.ThingName;

import java.util.Map;

public interface EnvironmentHelper {
    String GROUP_ID = "GROUP_ID";
    String AWS_IOT_THING_NAME = "AWS_IOT_THING_NAME";
    String AWS_IOT_THING_ARN = "AWS_IOT_THING_ARN";
    String AWS_GREENGRASS_GROUP_NAME = "AWS_GREENGRASS_GROUP_NAME";
    String REGION = "REGION";
    String ACCOUNT_ID = "ACCOUNT_ID";

    Map<String, String> getDefaultEnvironment(GreengrassGroupId greengrassGroupId, ThingName coreThingName, ThingArn coreThingArn, GreengrassGroupName greengrassGroupName);
}