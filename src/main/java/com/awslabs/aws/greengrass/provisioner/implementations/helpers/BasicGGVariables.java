package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.greengrass.model.FunctionIsolationMode;

import javax.inject.Inject;

public class BasicGGVariables implements GGVariables {
    @Inject
    public
    GGConstants ggConstants;

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
        return "greengrass.iot." + region.toString() + ".amazonaws.com";
    }

    @Override
    public String getDeviceDefinitionName(String groupName) {
        return groupName + "_DeviceDefinition";
    }

    @Override
    public String getGgdArchiveName(String groupName) {
        return String.join("/", ggConstants.getBuildDirectory(), String.join(".", "ggd", groupName, "tar"));
    }

    @Override
    public String getOemArchiveName(String groupName) {
        return String.join("/", ggConstants.getBuildDirectory(), String.join(".", "oem", groupName, "tar"));
    }

    @Override
    public String getGgShScriptName(String groupName) {
        return String.join("/", ggConstants.getBuildDirectory(), getBaseGgScriptName(groupName));
    }

    @Override
    public String getBaseGgScriptName(String groupName) {
        return String.join(".", "gg", groupName, "sh");
    }

    @Override
    public Config getFunctionDefaults() {
        return ConfigFactory.parseFile(ggConstants.getFunctionDefaultsConf());
    }

    @Override
    public FunctionIsolationMode getDefaultFunctionIsolationMode() {
        boolean greengrassContainer = getFunctionDefaults().getBoolean(ggConstants.getConfGreengrassContainer());

        return (greengrassContainer ? FunctionIsolationMode.GREENGRASS_CONTAINER : FunctionIsolationMode.NO_CONTAINER);
    }

}

