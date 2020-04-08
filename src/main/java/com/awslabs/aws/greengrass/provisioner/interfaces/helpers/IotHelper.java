package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.iot.data.*;

import java.util.Optional;

public interface IotHelper {
    Optional<KeysAndCertificate> loadKeysAndCertificateForCore(GreengrassGroupName greengrassGroupName);

    Optional<KeysAndCertificate> loadKeysAndCertificate(GreengrassGroupName greengrassGroupName, String deviceName);

    KeysAndCertificate createKeysAndCertificateForCore(GreengrassGroupName greengrassGroupName);

    KeysAndCertificate createKeysAndCertificate(GreengrassGroupName greengrassGroupName, String deviceName);

    void writeKeysAndCertificateFile(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName, String deviceName);

    void writePublicSignedCertificateFileForCore(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName);

    void writePublicSignedCertificateFile(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName, String deviceName);

    void writePrivateKeyFileForCore(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName);

    void writePrivateKeyFile(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName, String deviceName);

    void writeRootCaFile(GreengrassGroupName greengrassGroupName);

    void writeIotCpPropertiesFile(GreengrassGroupName greengrassGroupName, ThingName coreThingName, RoleAlias coreRoleAlias);
}
