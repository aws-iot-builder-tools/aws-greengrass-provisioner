package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.iot.data.ThingArn;
import software.amazon.awssdk.regions.Region;

public interface ConfigFileHelper {
    String generateConfigJson(String caPath,
                              String certPath,
                              String keyPath,
                              ThingArn coreThingArn,
                              String iotHost,
                              Region region,
                              DeploymentArguments deploymentArguments,
                              boolean functionsRunningAsRoot);
}
