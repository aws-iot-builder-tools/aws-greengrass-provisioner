package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.AwsHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.EnvironmentHelper;
import com.awslabs.iam.helpers.interfaces.V2IamHelper;
import com.awslabs.iot.data.GreengrassGroupId;
import com.awslabs.iot.data.GreengrassGroupName;
import com.awslabs.iot.data.ThingArn;
import com.awslabs.iot.data.ThingName;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class BasicEnvironmentHelper implements EnvironmentHelper {
    @Inject
    AwsHelper awsHelper;
    @Inject
    V2IamHelper iamHelper;

    @Inject
    public BasicEnvironmentHelper() {
    }

    @Override
    public Map<String, String> getDefaultEnvironment(GreengrassGroupId greengrassGroupId, ThingName coreThingName, ThingArn coreThingArn, GreengrassGroupName greengrassGroupName) {
        Map<String, String> defaultEnvironment = new HashMap<>();

        // These values go into the default environment (even though AWS_IOT_THING_NAME is already there) and they also
        //   allow us to use them as variables in the function.conf files
        // NOTE: These must match the values in functions.defaults.conf!
        defaultEnvironment.put(GROUP_ID, greengrassGroupId.getGroupId());
        defaultEnvironment.put(AWS_IOT_THING_NAME, coreThingName.getName());
        defaultEnvironment.put(AWS_IOT_THING_ARN, coreThingArn.getArn());
        defaultEnvironment.put(AWS_GREENGRASS_GROUP_NAME, greengrassGroupName.getGroupName());
        defaultEnvironment.put(REGION, awsHelper.getCurrentRegion().id());
        defaultEnvironment.put(ACCOUNT_ID, iamHelper.getAccountId());

        return defaultEnvironment;
    }
}
