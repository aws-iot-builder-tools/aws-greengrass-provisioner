package com.awslabs.aws.greengrass.provisioner.data;

import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.services.iot.model.KeyPair;
import com.awslabs.aws.greengrass.provisioner.data.ImmutableKeysAndCertificate;

@Value.Immutable
public abstract class KeysAndCertificate {
    public abstract String getCertificateArn();

    public abstract String getCertificateId();

    public abstract String getCertificatePem();

    public abstract KeyPair getKeyPair();

    public static KeysAndCertificate from(CreateKeysAndCertificateResponse createKeysAndCertificateResponse) {
        KeysAndCertificate keysAndCertificate = ImmutableKeysAndCertificate.builder()
                .certificateArn(createKeysAndCertificateResponse.certificateArn())
                .certificateId(createKeysAndCertificateResponse.certificateId())
                .certificatePem(createKeysAndCertificateResponse.certificatePem())
                .keyPair(createKeysAndCertificateResponse.keyPair()).build();

        return keysAndCertificate;
    }
}
