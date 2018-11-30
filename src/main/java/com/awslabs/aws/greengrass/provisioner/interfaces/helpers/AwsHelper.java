package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import software.amazon.awssdk.regions.Region;

public interface AwsHelper {
    Region getCurrentRegion();
}
