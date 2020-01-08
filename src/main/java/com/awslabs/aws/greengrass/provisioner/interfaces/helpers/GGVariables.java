package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.typesafe.config.Config;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.greengrass.model.FunctionIsolationMode;

public interface GGVariables {
    String getCoreThingName(String groupName);

    String getCoreDefinitionName(String groupName);

    String getCorePolicyName(String groupName);

    String getDeviceShadowTopicFilterName(String deviceThingName);

    String getGgHost(Region region);

    String getDeviceDefinitionName(String groupName);

    String getGgdArchiveName(String groupName);

    String getOemArchiveName(String groupName);

    String getGgShScriptName(String groupName);

    String getBaseGgScriptName(String groupName);

    Config getFunctionDefaults();

    Config getConnectorDefaults();

    FunctionIsolationMode getDefaultFunctionIsolationMode();
}

