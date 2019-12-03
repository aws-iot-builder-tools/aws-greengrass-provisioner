package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.HsiParameters;
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
        Map<String, Object> coreThingMap = new HashMap<>();
        Map<String, Object> runtimeMap = new HashMap<>();
        Map<String, Object> cgroupMap = new HashMap<>();
        Map<String, Object> cryptoMap = new HashMap<>();
        Map<String, Object> principalsMap = new HashMap<>();
        Map<String, Object> SecretsManagerMap = new HashMap<>();
        Map<String, Object> IoTCertificateMap = new HashMap<>();
        Map<String, Object> MQTTServerCertificate = new HashMap<>();
        Map<String, Object> PKCS11Map = new HashMap<>();

        coreThingMap.put("thingArn", coreThingArn);
        coreThingMap.put("iotHost", iotHost);
        coreThingMap.put("ggHost", ggVariables.getGgHost(region));
        coreThingMap.put("ggMqttPort", deploymentArguments.mqttPort);

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

        IoTCertificateMap.put("certificatePath", CERTS_URI + certPath);

        principalsMap.put("SecretsManager", SecretsManagerMap);
        principalsMap.put("IoTCertificate", IoTCertificateMap);
        principalsMap.put("MQTTServerCertificate", MQTTServerCertificate);

        if (deploymentArguments.hsiParameters != null) {
            HsiParameters hsiParameters = deploymentArguments.hsiParameters;

            SecretsManagerMap.put("privateKeyPath", hsiParameters.getPkcsPath());
            IoTCertificateMap.put("privateKeyPath", hsiParameters.getPkcsPath());
            MQTTServerCertificate.put("privateKeyPath", hsiParameters.getPkcsPath());

            PKCS11Map.put("P11Provider", hsiParameters.getP11Provider());
            PKCS11Map.put("slotLabel", hsiParameters.getSlotLabel());
            PKCS11Map.put("slotUserPin", hsiParameters.getSlotUserPin());

            cryptoMap.put("PKCS11", PKCS11Map);
        } else {
            // Avoids "private key for MqttCertificate is not set" error/warning
            SecretsManagerMap.put("privateKeyPath", CERTS_URI + keyPath);
            IoTCertificateMap.put("privateKeyPath", CERTS_URI + keyPath);
            MQTTServerCertificate.put("privateKeyPath", CERTS_URI + keyPath);
        }

        cryptoMap.put("caPath", CERTS_URI + caPath);

        Map<String, Object> config = new HashMap<>();

        config.put("coreThing", coreThingMap);
        config.put("runtime", runtimeMap);
        config.put("managedRespawn", false);
        config.put("crypto", cryptoMap);

        return jsonHelper.toJson(config);
    }
}
