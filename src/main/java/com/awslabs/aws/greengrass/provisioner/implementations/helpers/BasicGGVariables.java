package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.regions.Region;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;

import javax.inject.Inject;

public class BasicGGVariables implements GGVariables {
    @Inject
    public BasicGGVariables() {
    }

    @Override
    public String getCoreThingName(String groupName) {
        return groupName + "_Core";
    }

    @Override
    public String getCoreDefinitionName(String groupName) {
        return getCoreThingName(groupName) + "_Definition";
    }

    @Override
    public String getCorePolicyName(String groupName) {
        return getCoreThingName(groupName) + "_Policy";
    }

    @Override
    public String getDeviceShadowTopicFilterName(String deviceThingName) {
        return "$aws/things/" + deviceThingName + "/shadow/#";
    }

    @Override
    public String getGgHost(Region region) {
        return "greengrass.iot." + region.getName() + ".amazonaws.com";
    }

    @Override
    public String getDeviceDefinitionName(String groupName) {
        return groupName + "_DeviceDefinition";
    }
}

