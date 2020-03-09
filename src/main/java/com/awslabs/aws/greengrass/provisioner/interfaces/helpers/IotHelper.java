package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.model.CreateRoleAliasResponse;

import java.util.Optional;

public interface IotHelper {
    String createThing(String name);

    Optional<KeysAndCertificate> loadKeysAndCertificate(String groupId, String subName);

    KeysAndCertificate createKeysAndCertificate(String groupId, String subName);
}
