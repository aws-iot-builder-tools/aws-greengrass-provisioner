package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.iot.data.GreengrassGroupId;

import java.util.Optional;

public interface IotHelper {
    Optional<KeysAndCertificate> loadKeysAndCertificate(GreengrassGroupId greengrassGroupId, String subName);

    KeysAndCertificate createKeysAndCertificate(GreengrassGroupId greengrassGroupId, String subName);
}
