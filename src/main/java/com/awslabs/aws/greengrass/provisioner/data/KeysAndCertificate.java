package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.aws.greengrass.provisioner.data.ImmutableKeysAndCertificate;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.services.iot.model.KeyPair;

@Value.Immutable
public abstract class KeysAndCertificate {
    public static KeysAndCertificate from(CreateKeysAndCertificateResponse createKeysAndCertificateResponse) {
        KeysAndCertificate keysAndCertificate = ImmutableKeysAndCertificate.builder()
                .certificateArn(createKeysAndCertificateResponse.certificateArn())
                .certificateId(createKeysAndCertificateResponse.certificateId())
                .certificatePem(createKeysAndCertificateResponse.certificatePem())
                .keyPair(createKeysAndCertificateResponse.keyPair()).build();

        return keysAndCertificate;
    }

    public abstract String getCertificateArn();

    public abstract String getCertificateId();

    public abstract String getCertificatePem();

    public abstract KeyPair getKeyPair();
}
