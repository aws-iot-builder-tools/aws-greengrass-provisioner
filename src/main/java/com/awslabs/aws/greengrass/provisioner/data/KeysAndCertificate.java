package com.awslabs.aws.greengrass.provisioner.data;

import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;
import lombok.Builder;
import lombok.Data;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.services.iot.model.KeyPair;

@Data
@Builder
public class KeysAndCertificate {
    private final String certificateArn;

    private final String certificateId;

    private final String certificatePem;

    private final KeyPair keyPair;

    public static KeysAndCertificate from(CreateKeysAndCertificateResponse createKeysAndCertificateResponse) {
        KeysAndCertificate keysAndCertificate = KeysAndCertificate.builder()
                .certificateArn(createKeysAndCertificateResponse.certificateArn())
                .certificateId(createKeysAndCertificateResponse.certificateId())
                .certificatePem(createKeysAndCertificateResponse.certificatePem())
                .keyPair(createKeysAndCertificateResponse.keyPair()).build();

        return keysAndCertificate;
    }

    public static KeysAndCertificate from(CreateKeysAndCertificateResult createKeysAndCertificateResult) {
        KeysAndCertificate keysAndCertificate = KeysAndCertificate.builder()
                .certificateArn(createKeysAndCertificateResult.getCertificateArn())
                .certificateId(createKeysAndCertificateResult.getCertificateId())
                .certificatePem(createKeysAndCertificateResult.getCertificatePem())
                .keyPair(KeyPair.builder()
                        .privateKey(createKeysAndCertificateResult.getKeyPair().getPrivateKey())
                        .publicKey(createKeysAndCertificateResult.getKeyPair().getPublicKey())
                        .build())
                .build();

        return keysAndCertificate;
    }
}
