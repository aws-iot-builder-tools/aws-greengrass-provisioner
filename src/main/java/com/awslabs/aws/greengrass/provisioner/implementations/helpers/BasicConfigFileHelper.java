package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.DeploymentArguments;
import com.awslabs.aws.greengrass.provisioner.data.arguments.HsiParameters;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ConfigFileHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import software.amazon.awssdk.regions.Region;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class BasicConfigFileHelper implements ConfigFileHelper {
    public static final String CERTS_URI = "file://certs/";
    public static final String USE_SYSTEMD = "useSystemd";
    public static final String NO = "no";
    public static final String YES = "yes";
    public static final String THING_ARN = "thingArn";
    public static final String IOT_HOST = "iotHost";
    public static final String GG_HOST = "ggHost";
    public static final String GG_MQTT_PORT = "ggMqttPort";
    public static final String CGROUP = "cgroup";
    public static final String ALLOW_FUNCTIONS_TO_RUN_AS_ROOT = "allowFunctionsToRunAsRoot";
    public static final String PRINCIPALS = "principals";
    public static final String CERTIFICATE_PATH = "certificatePath";
    public static final String SECRETS_MANAGER = "SecretsManager";
    public static final String IOT_CERTIFICATE = "IoTCertificate";
    public static final String MQTT_SERVER_CERTIFICATE = "MQTTServerCertificate";
    public static final String PRIVATE_KEY_PATH = "privateKeyPath";
    public static final String P_11_PROVIDER = "P11Provider";
    public static final String SLOT_LABEL = "slotLabel";
    public static final String SLOT_USER_PIN = "slotUserPin";
    public static final String PKCS_11 = "PKCS11";
    public static final String CA_PATH = "caPath";
    public static final String CORE_THING = "coreThing";
    public static final String RUNTIME = "runtime";
    public static final String MANAGED_RESPAWN = "managedRespawn";
    public static final String CRYPTO = "crypto";
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

        coreThingMap.put(THING_ARN, coreThingArn);
        coreThingMap.put(IOT_HOST, iotHost);
        coreThingMap.put(GG_HOST, ggVariables.getGgHost(region));
        coreThingMap.put(GG_MQTT_PORT, deploymentArguments.mqttPort);

        if (deploymentArguments.noSystemD) {
            cgroupMap.put(USE_SYSTEMD, NO);
        } else {
            cgroupMap.put(USE_SYSTEMD, YES);
        }

        runtimeMap.put(CGROUP, cgroupMap);

        if (functionsRunningAsRoot) {
            runtimeMap.put(ALLOW_FUNCTIONS_TO_RUN_AS_ROOT, YES);
        }

        cryptoMap.put(PRINCIPALS, principalsMap);

        IoTCertificateMap.put(CERTIFICATE_PATH, CERTS_URI + certPath);

        principalsMap.put(SECRETS_MANAGER, SecretsManagerMap);
        principalsMap.put(IOT_CERTIFICATE, IoTCertificateMap);
        principalsMap.put(MQTT_SERVER_CERTIFICATE, MQTTServerCertificate);

        if (deploymentArguments.hsiParameters != null) {
            HsiParameters hsiParameters = deploymentArguments.hsiParameters;

            SecretsManagerMap.put(PRIVATE_KEY_PATH, hsiParameters.getPkcsPath());
            IoTCertificateMap.put(PRIVATE_KEY_PATH, hsiParameters.getPkcsPath());
            MQTTServerCertificate.put(PRIVATE_KEY_PATH, hsiParameters.getPkcsPath());

            PKCS11Map.put(P_11_PROVIDER, hsiParameters.getP11Provider());
            PKCS11Map.put(SLOT_LABEL, hsiParameters.getSlotLabel());
            PKCS11Map.put(SLOT_USER_PIN, hsiParameters.getSlotUserPin());

            cryptoMap.put(PKCS_11, PKCS11Map);
        } else {
            // Avoids "private key for MqttCertificate is not set" error/warning
            SecretsManagerMap.put(PRIVATE_KEY_PATH, CERTS_URI + keyPath);
            IoTCertificateMap.put(PRIVATE_KEY_PATH, CERTS_URI + keyPath);
            MQTTServerCertificate.put(PRIVATE_KEY_PATH, CERTS_URI + keyPath);
        }

        cryptoMap.put(CA_PATH, CERTS_URI + caPath);

        Map<String, Object> config = new HashMap<>();

        config.put(CORE_THING, coreThingMap);
        config.put(RUNTIME, runtimeMap);
        config.put(MANAGED_RESPAWN, false);
        config.put(CRYPTO, cryptoMap);

        return jsonHelper.toJson(config);
    }
}
