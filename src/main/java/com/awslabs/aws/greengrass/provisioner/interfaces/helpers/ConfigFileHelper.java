package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.amazonaws.regions.Region;
import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;

public interface ConfigFileHelper {
    String generateConfigJson(String caPath, String certPath, String keyPath, String coreThingArn, String iotHost, Region region, DeploymentArguments deploymentArguments);
}
