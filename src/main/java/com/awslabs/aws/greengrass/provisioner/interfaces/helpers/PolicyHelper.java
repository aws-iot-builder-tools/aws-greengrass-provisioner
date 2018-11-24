package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

public interface PolicyHelper {
    String buildDevicePolicyDocument(String deviceThingArn);
}
