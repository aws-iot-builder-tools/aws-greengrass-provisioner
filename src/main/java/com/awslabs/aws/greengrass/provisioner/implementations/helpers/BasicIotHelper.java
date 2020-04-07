package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.iot.data.GreengrassGroupId;
import com.awslabs.iot.data.ImmutableThingName;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;

import javax.inject.Inject;
import java.util.Optional;

public class BasicIotHelper implements IotHelper {
    private static final String BUILD_DIRECTORY = "build/";
    private static final String PEM = "pem";
    private static final String KEY = "key";
    private static final String CRT = "crt";
    private static final String DOT_DELIMITER = ".";
    private static final String CREDENTIALS = "credentials/";
    private final Logger log = LoggerFactory.getLogger(BasicIotHelper.class);
    @Inject
    IotClient iotClient;
    @Inject
    IoHelper ioHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    V2IotHelper v2IotHelper;

    @Inject
    public BasicIotHelper() {
    }

    private String credentialDirectoryForGroupId(GreengrassGroupId greengrassGroupId) {
        return CREDENTIALS + greengrassGroupId.getGroupId();
    }

    private String createKeysandCertificateFilenameForGroupId(GreengrassGroupId greengrassGroupId, String subName) {
        return credentialDirectoryForGroupId(greengrassGroupId) + "/" + subName + ".createKeysAndCertificate.serialized";
    }

    @Override
    public Optional<KeysAndCertificate> loadKeysAndCertificate(GreengrassGroupId greengrassGroupId, String subName) {
        String credentialsDirectory = credentialDirectoryForGroupId(greengrassGroupId);

        ioHelper.createDirectoryIfNecessary(credentialsDirectory);

        String createKeysAndCertificateFilename = createKeysandCertificateFilenameForGroupId(greengrassGroupId, subName);

        if (ioHelper.exists(createKeysAndCertificateFilename)) {
            log.info("- Attempting to reuse existing keys.");

            KeysAndCertificate keysAndCertificate = jsonHelper.fromJson(KeysAndCertificate.class, ioHelper.readFile(createKeysAndCertificateFilename));

            if (v2IotHelper.certificateExists(keysAndCertificate.getCertificateId())) {
                log.info("- Reusing existing keys.");
                return Optional.of(keysAndCertificate);
            }

            log.warn("- Existing certificate is not in AWS IoT.  It may have been deleted.");
            return Optional.empty();
        }

        log.warn("- No existing keys found for group.");
        return Optional.empty();
    }

    @Override
    public KeysAndCertificate createKeysAndCertificate(GreengrassGroupId greengrassGroupId, String subName) {
        String credentialsDirectory = credentialDirectoryForGroupId(greengrassGroupId);

        ioHelper.createDirectoryIfNecessary(credentialsDirectory);

        String createKeysAndCertificateFilename = createKeysandCertificateFilenameForGroupId(greengrassGroupId, subName);

        // Let them know that they'll need to re-run the bootstrap script because the core's keys changed
        boolean isCore = subName.equals(DeploymentHelper.CORE_SUB_NAME);
        String supplementalMessage = isCore ? "  If you have an existing deployment for this group you'll need to re-run the bootstrap script since the core certificate ARN will change." : "";
        log.info("- Creating new keys." + supplementalMessage);
        CreateKeysAndCertificateRequest createKeysAndCertificateRequest = CreateKeysAndCertificateRequest.builder()
                .setAsActive(true)
                .build();

        CreateKeysAndCertificateResponse createKeysAndCertificateResponse = iotClient.createKeysAndCertificate(createKeysAndCertificateRequest);

        String deviceName = greengrassGroupId.getGroupId();
        String privateKeyFilename = getPrivateKeyFilename(deviceName);
        String publicSignedCertificateFilename = getPublicSignedCertificateFilename(deviceName);

        ioHelper.writeFile(privateKeyFilename, createKeysAndCertificateResponse.keyPair().privateKey().getBytes());
        log.info("Device private key written to [" + privateKeyFilename + "]");
        ioHelper.writeFile(publicSignedCertificateFilename, createKeysAndCertificateResponse.certificatePem().getBytes());
        log.info("Device public signed certificate key written to [" + publicSignedCertificateFilename + "]");

        KeysAndCertificate keysAndCertificate = KeysAndCertificate.from(createKeysAndCertificateResponse);
        ioHelper.writeFile(createKeysAndCertificateFilename, jsonHelper.toJson(keysAndCertificate).getBytes());

        return keysAndCertificate;
    }

    @NotNull
    public String getPublicSignedCertificateFilename(String deviceName) {
        return BUILD_DIRECTORY + String.join(DOT_DELIMITER, deviceName, PEM);
    }

    @NotNull
    public String getPrivateKeyFilename(String deviceName) {
        return BUILD_DIRECTORY + String.join(DOT_DELIMITER, deviceName, KEY);
    }
}
