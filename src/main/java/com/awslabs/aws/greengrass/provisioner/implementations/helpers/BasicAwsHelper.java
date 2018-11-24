package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.regions.AwsRegionProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.AwsHelper;

import javax.inject.Inject;

public class BasicAwsHelper implements AwsHelper {
    @Inject
    AwsRegionProviderChain awsRegionProviderChain;

    @Inject
    public BasicAwsHelper() {
    }

    @Override
    public Region getCurrentRegion() {
        String regionString = awsRegionProviderChain.getRegion();

        return Region.getRegion(Regions.fromName(regionString));
    }
}
