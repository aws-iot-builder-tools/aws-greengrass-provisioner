package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.AwsHelper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProviderChain;

import javax.inject.Inject;

public class BasicAwsHelper implements AwsHelper {
    @Inject
    AwsRegionProviderChain awsRegionProviderChain;

    @Inject
    public BasicAwsHelper() {
    }

    @Override
    public Region getCurrentRegion() {
        return awsRegionProviderChain.getRegion();
    }
}
