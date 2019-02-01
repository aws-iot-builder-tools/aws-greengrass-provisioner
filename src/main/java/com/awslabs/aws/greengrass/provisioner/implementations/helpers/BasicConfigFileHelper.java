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
    public static final String PKCS11_SOFTHSM2_PATH = "pkcs11:object=iotkey;type=private";
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
        Map PKCS11Map = new HashMap();

        if (!deploymentArguments.hsiSoftHsm2) {
            coreThingMap.put("caPath", caPath);
            coreThingMap.put("certPath", certPath);
            coreThingMap.put("keyPath", keyPath);
        }

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

        IoTCertificateMap.put("certificatePath", CERTS_URI + certPath);

        principalsMap.put("SecretsManager", SecretsManagerMap);
        principalsMap.put("IoTCertificate", IoTCertificateMap);
        principalsMap.put("MQTTServerCertificate", MQTTServerCertificate);

        if (deploymentArguments.hsiSoftHsm2) {
            SecretsManagerMap.put("privateKeyPath", PKCS11_SOFTHSM2_PATH);
            IoTCertificateMap.put("privateKeyPath", PKCS11_SOFTHSM2_PATH);
            MQTTServerCertificate.put("privateKeyPath", PKCS11_SOFTHSM2_PATH);

            PKCS11Map.put("P11Provider", "/greengrass/libsofthsm2.so");
            PKCS11Map.put("slotLabel", "greengrass");
            PKCS11Map.put("slotUserPin", "1234");

            cryptoMap.put("PKCS11", PKCS11Map);
        } else {
            // Avoids "private key for MqttCertificate is not set" error/warning
            SecretsManagerMap.put("privateKeyPath", CERTS_URI + keyPath);
            IoTCertificateMap.put("privateKeyPath", CERTS_URI + keyPath);
            MQTTServerCertificate.put("privateKeyPath", CERTS_URI + keyPath);
        }

        cryptoMap.put("caPath", CERTS_URI + caPath);

        Map config = new HashMap();

        config.put("coreThing", coreThingMap);
        config.put("runtime", runtimeMap);
        config.put("managedRespawn", false);
        config.put("crypto", cryptoMap);

        return jsonHelper.toJson(config);
    }
}
