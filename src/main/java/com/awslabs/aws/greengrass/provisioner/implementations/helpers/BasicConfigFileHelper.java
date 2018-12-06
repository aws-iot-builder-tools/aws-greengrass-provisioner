package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ConfigFileHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import software.amazon.awssdk.regions.Region;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class BasicConfigFileHelper implements ConfigFileHelper {
    public static final String CERTS_URI = "file://certs/";
    @Inject
    GGVariables ggVariables;
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public BasicConfigFileHelper() {
    }

    @Override
    public String generateConfigJson(String caPath, String certPath, String keyPath, String coreThingArn, String iotHost, Region region, DeploymentArguments deploymentArguments, boolean functionsRunningAsRoot) {
        Map coreThingMap = new HashMap();
        Map runtimeMap = new HashMap();
        Map cgroupMap = new HashMap();
        Map cryptoMap = new HashMap();
        Map principalsMap = new HashMap();
        Map SecretsManagerMap = new HashMap();
        Map IoTCertificateMap = new HashMap();
        Map MQTTServerCertificate = new HashMap();

        coreThingMap.put("caPath", caPath);
        coreThingMap.put("certPath", certPath);
        coreThingMap.put("keyPath", keyPath);
        coreThingMap.put("thingArn", coreThingArn);
        coreThingMap.put("iotHost", iotHost);
        coreThingMap.put("ggHost", ggVariables.getGgHost(region));

        if (deploymentArguments.noSystemD) {
            cgroupMap.put("useSystemd", "no");
        } else {
            cgroupMap.put("useSystemd", "yes");
        }

        runtimeMap.put("cgroup", cgroupMap);

        if (functionsRunningAsRoot) {
            runtimeMap.put("allowFunctionsToRunAsRoot", "yes");
        }

        cryptoMap.put("principals", principalsMap);

        principalsMap.put("SecretsManager", SecretsManagerMap);
        principalsMap.put("IoTCertificate", IoTCertificateMap);
        principalsMap.put("MQTTServerCertificate", MQTTServerCertificate);

        SecretsManagerMap.put("privateKeyPath", CERTS_URI + keyPath);

        IoTCertificateMap.put("privateKeyPath", CERTS_URI + keyPath);
        IoTCertificateMap.put("certificatePath", CERTS_URI + certPath);

        // Avoids "private key for MqttCertificate is not set" error/warning
        MQTTServerCertificate.put("privateKeyPath", CERTS_URI + keyPath);

        cryptoMap.put("caPath", CERTS_URI + caPath);

        Map config = new HashMap();

        config.put("coreThing", coreThingMap);
        config.put("runtime", runtimeMap);
        config.put("managedRespawn", false);
        config.put("crypto", cryptoMap);

        return jsonHelper.toJson(config);
    }
}
