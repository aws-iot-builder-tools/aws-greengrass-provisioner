package com.awslabs.aws.greengrass.provisioner.data;

import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicJsonHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.iot.model.KeyPair;

import static org.hamcrest.CoreMatchers.is;

public class KeysAndCertificateSerializationTests {
    private JsonHelper jsonHelper;
    private KeysAndCertificate keysAndCertificate;
    private KeyPair keyPair;

    @Before
    public void setup() {
        jsonHelper = new BasicJsonHelper();
        keyPair = KeyPair.builder()
                .privateKey("privateKey123")
                .publicKey("publicKey123")
                .build();
        keysAndCertificate = com.awslabs.aws.greengrass.provisioner.data.ImmutableKeysAndCertificate.builder()
                .certificateArn("certificateArn123")
                .certificateId("certificateId123")
                .certificatePem("certificatePem123")
                .keyPair(keyPair)
                .build();
    }

    @Test
    public void shouldSerializeWithoutExceptions() {
        jsonHelper.toJson(keysAndCertificate);
    }

    @Test
    public void shouldDeserializeWithoutExceptions() {
        jsonHelper.fromJson(KeysAndCertificate.class, jsonHelper.toJson(keysAndCertificate).getBytes());
    }

    @Test
    public void shouldDeserializeAndCreateIdenticalObject() {
        KeysAndCertificate testKeysAndCertificate = jsonHelper.fromJson(KeysAndCertificate.class, jsonHelper.toJson(keysAndCertificate).getBytes());

        MatcherAssert.assertThat(testKeysAndCertificate, is(keysAndCertificate));
    }
}
