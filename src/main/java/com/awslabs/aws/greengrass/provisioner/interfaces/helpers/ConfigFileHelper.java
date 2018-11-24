package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.regions.Region;

public interface ConfigFileHelper {
    String generateConfigJson(String caPath, String certPath, String keyPath, String coreThingArn, String iotHost, Region region);
}
