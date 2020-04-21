package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.iot.data.*;
import org.immutables.gson.Gson;
import org.immutables.value.Value;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;
import software.amazon.awssdk.services.iot.model.KeyPair;

@Gson.TypeAdapters
@Value.Immutable
public abstract class KeysAndCertificate {
    public static KeysAndCertificate from(CreateKeysAndCertificateResponse createKeysAndCertificateResponse) {
        return ImmutableKeysAndCertificate.builder()
                .certificateArn(ImmutableCertificateArn.builder().arn(createKeysAndCertificateResponse.certificateArn()).build())
                .certificateId(ImmutableCertificateId.builder().id(createKeysAndCertificateResponse.certificateId()).build())
                .certificatePem(ImmutableCertificatePem.builder().pem(createKeysAndCertificateResponse.certificatePem()).build())
                .keyPair(createKeysAndCertificateResponse.keyPair())
                .build();
    }

    public abstract ImmutableCertificateArn getCertificateArn();

    public abstract ImmutableCertificateId getCertificateId();

    public abstract ImmutableCertificatePem getCertificatePem();

    public abstract KeyPair getKeyPair();
}
