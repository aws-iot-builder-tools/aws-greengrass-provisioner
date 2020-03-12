package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.iot.data.ThingArn;

public interface PolicyHelper {
    String buildDevicePolicyDocument(ThingArn deviceThingArn);
}
