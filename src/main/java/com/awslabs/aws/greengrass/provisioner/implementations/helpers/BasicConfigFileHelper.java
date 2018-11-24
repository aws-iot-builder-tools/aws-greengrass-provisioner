package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.regions.Region;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ConfigFileHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class BasicConfigFileHelper implements ConfigFileHelper {
    @Inject
    GGVariables ggVariables;
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public BasicConfigFileHelper() {
    }

    @Override
    public String generateConfigJson(String caPath, String certPath, String keyPath, String coreThingArn, String iotHost, Region region) {
        Map coreThingMap = new HashMap();
        Map runtimeMap = new HashMap();
        Map cgroupMap = new HashMap();

        coreThingMap.put("caPath", caPath);
        coreThingMap.put("certPath", certPath);
        coreThingMap.put("keyPath", keyPath);
        coreThingMap.put("thingArn", coreThingArn);
        coreThingMap.put("iotHost", iotHost);
        coreThingMap.put("ggHost", ggVariables.getGgHost(region));

        cgroupMap.put("useSystemd", "yes");

        runtimeMap.put("cgroup", cgroupMap);

        Map config = new HashMap();

        config.put("coreThing", coreThingMap);
        config.put("runtime", runtimeMap);
        config.put("managedRespawn", false);

        return jsonHelper.toJson(config);
    }
}
