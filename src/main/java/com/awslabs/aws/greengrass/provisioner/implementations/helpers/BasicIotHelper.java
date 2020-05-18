package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.iot.data.GreengrassGroupName;
import com.awslabs.iot.data.RoleAlias;
import com.awslabs.iot.data.ThingName;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import io.vavr.control.Try;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateRequest;
import software.amazon.awssdk.services.iot.model.CreateKeysAndCertificateResponse;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import static com.awslabs.resultsiterator.v2.interfaces.V2CertificateCredentialsProvider.*;

public class BasicIotHelper implements IotHelper {
    private static final String CORE_DEVICE_NAME = "core";
    private static final String PEM = "pem";
    private static final String KEY = "key";
    private static final String DOT_DELIMITER = ".";
    private static final String CREDENTIALS_DIRECTORY_PREFIX = "credentials";
    private final Logger log = LoggerFactory.getLogger(BasicIotHelper.class);
    @Inject
    IotClient iotClient;
    @Inject
    IoHelper ioHelper;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    V2IotHelper v2IotHelper;
    @Inject
    GGConstants ggConstants;

    @Inject
    public BasicIotHelper() {
    }

    private String getCredentialsDirectoryForGroupName(GreengrassGroupName greengrassGroupName) {
        return String.join("/", CREDENTIALS_DIRECTORY_PREFIX, greengrassGroupName.getGroupName());
    }

    @Override
    public Optional<KeysAndCertificate> loadKeysAndCertificateForCore(GreengrassGroupName greengrassGroupName) {
        return loadKeysAndCertificate(greengrassGroupName, CORE_DEVICE_NAME);
    }

    @Override
    public Optional<KeysAndCertificate> loadKeysAndCertificate(GreengrassGroupName greengrassGroupName, String deviceName) {
        ioHelper.createDirectoryIfNecessary(getCredentialsDirectoryForGroupName(greengrassGroupName));

        String createKeysAndCertificateFilename = getCreateKeysAndCertificateFilenameForGroupName(greengrassGroupName, deviceName);

        if (!ioHelper.exists(createKeysAndCertificateFilename)) {
            log.warn("- No existing keys found for group.");
            return Optional.empty();
        }

        log.info("- Attempting to reuse existing keys.");

        KeysAndCertificate keysAndCertificate = jsonHelper.fromJson(KeysAndCertificate.class, ioHelper.readFile(createKeysAndCertificateFilename));

        if (v2IotHelper.certificateExists(keysAndCertificate.getCertificateId())) {
            log.info("- Reusing existing keys.");
            return Optional.of(keysAndCertificate);
        }

        log.warn("- Existing certificate is not in AWS IoT.  It may have been deleted.");
        return Optional.empty();
    }

    @Override
    public KeysAndCertificate createKeysAndCertificateForCore(GreengrassGroupName greengrassGroupName) {
        return createKeysAndCertificate(greengrassGroupName, CORE_DEVICE_NAME);
    }

    @Override
    public KeysAndCertificate createKeysAndCertificate(GreengrassGroupName greengrassGroupName, String deviceName) {
        ioHelper.createDirectoryIfNecessary(getCredentialsDirectoryForGroupName(greengrassGroupName));

        // Let them know that they'll need to re-run the bootstrap script because the core's keys changed
        boolean isCore = CORE_DEVICE_NAME.equals(deviceName);
        String supplementalMessage = isCore ? "  If you have an existing deployment for this group you'll need to re-run the bootstrap script since the core certificate ARN will change." : "";
        log.info(String.join("", "- Creating new keys.", supplementalMessage));
        CreateKeysAndCertificateRequest createKeysAndCertificateRequest = CreateKeysAndCertificateRequest.builder()
                .setAsActive(true)
                .build();

        CreateKeysAndCertificateResponse createKeysAndCertificateResponse = iotClient.createKeysAndCertificate(createKeysAndCertificateRequest);

        KeysAndCertificate keysAndCertificate = KeysAndCertificate.from(createKeysAndCertificateResponse);
        writeKeysAndCertificateFile(keysAndCertificate, greengrassGroupName, deviceName);

        return keysAndCertificate;
    }

    @Override
    public void writeKeysAndCertificateFile(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName, String subName) {
        String createKeysAndCertificateFilename = getCreateKeysAndCertificateFilenameForGroupName(greengrassGroupName, subName);
        ioHelper.writeFile(createKeysAndCertificateFilename, jsonHelper.toJson(keysAndCertificate).getBytes());
        log.info(String.join("", "Keys and certificate data written to [", createKeysAndCertificateFilename, "]"));
    }

    @Override
    public void writePublicSignedCertificateFileForCore(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName) {
        writePublicSignedCertificateFile(keysAndCertificate, greengrassGroupName, CORE_DEVICE_NAME);
    }

    @Override
    public void writePublicSignedCertificateFile(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName, String deviceName) {
        String publicSignedCertificateFilename = getPublicSignedCertificateFilename(greengrassGroupName, deviceName);
        ioHelper.writeFile(publicSignedCertificateFilename, keysAndCertificate.getCertificatePem().getPem().getBytes());
        log.info(String.join("", "Device public signed certificate key written to [", publicSignedCertificateFilename, "]"));
    }

    @Override
    public void writePrivateKeyFileForCore(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName) {
        writePrivateKeyFile(keysAndCertificate, greengrassGroupName, CORE_DEVICE_NAME);
    }

    @Override
    public void writePrivateKeyFile(KeysAndCertificate keysAndCertificate, GreengrassGroupName greengrassGroupName, String deviceName) {
        String privateKeyFilename = getPrivateKeyFilename(greengrassGroupName, deviceName);
        ioHelper.writeFile(privateKeyFilename, keysAndCertificate.getKeyPair().privateKey().getBytes());
        log.info(String.join("", "Device private key written to [", privateKeyFilename, "]"));
    }

    @Override
    public void writeRootCaFile(GreengrassGroupName greengrassGroupName) {
        String rootCaFilename = getRootCaFilename(greengrassGroupName);
        ioHelper.writeFile(rootCaFilename, ioHelper.download(ggConstants.getRootCaUrl()).getBytes());
        log.info(String.join("", "Root CA certificate written to [", rootCaFilename, "]"));
    }

    @Override
    public void writeIotCpPropertiesFile(GreengrassGroupName greengrassGroupName, ThingName coreThingName, RoleAlias coreRoleAlias) {
        Properties properties = new Properties();

        properties.setProperty(AWS_CREDENTIAL_PROVIDER_URL, v2IotHelper.getCredentialProviderUrl());
        properties.setProperty(AWS_THING_NAME, coreThingName.getName());
        properties.setProperty(AWS_ROLE_ALIAS, coreRoleAlias.getName());
        properties.setProperty(AWS_CA_CERT_FILENAME, toRelativePath(getRootCaFilename(greengrassGroupName)));
        properties.setProperty(AWS_CLIENT_CERT_FILENAME, toRelativePath(getPublicSignedCertificateFilenameForCore(greengrassGroupName)));
        properties.setProperty(AWS_CLIENT_PRIVATE_KEY_FILENAME, toRelativePath(getPrivateKeyFilenameForCore(greengrassGroupName)));

        Try.withResources(() -> new FileOutputStream(getIotCpPropertiesFilename(greengrassGroupName)))
                .of(fileOutputStream -> storeProperties(fileOutputStream, properties))
                .get();

        log.info(String.join("", "IoT Credentials Provider properties written to [", getIotCpPropertiesFilename(greengrassGroupName), "]"));
    }

    private String toRelativePath(String filename) {
        return new File(filename).getName();
    }

    private Void storeProperties(FileOutputStream fileOutputStream, Properties properties) throws IOException {
        properties.store(fileOutputStream, null);

        return null;
    }

    @NotNull
    private String getCreateKeysAndCertificateFilenameForGroupName(GreengrassGroupName greengrassGroupName, String deviceName) {
        return String.join("/", getCredentialsDirectoryForGroupName(greengrassGroupName), String.join(".", deviceName, "createKeysAndCertificate.serialized"));
    }

    @NotNull
    private String getPublicSignedCertificateFilenameForCore(GreengrassGroupName greengrassGroupName) {
        return getPublicSignedCertificateFilename(greengrassGroupName, CORE_DEVICE_NAME);
    }

    @NotNull
    private String getPublicSignedCertificateFilename(GreengrassGroupName greengrassGroupName, String deviceName) {
        return String.join("/", getCredentialsDirectoryForGroupName(greengrassGroupName), String.join(DOT_DELIMITER, deviceName, PEM));
    }

    @NotNull
    private String getRootCaFilename(GreengrassGroupName greengrassGroupName) {
        return String.join("/", getCredentialsDirectoryForGroupName(greengrassGroupName), String.join(DOT_DELIMITER, "root", "ca", PEM));
    }

    @NotNull
    private String getPrivateKeyFilenameForCore(GreengrassGroupName greengrassGroupName) {
        return getPrivateKeyFilename(greengrassGroupName, CORE_DEVICE_NAME);
    }

    @NotNull
    private String getPrivateKeyFilename(GreengrassGroupName greengrassGroupName, String deviceName) {
        return String.join("/", getCredentialsDirectoryForGroupName(greengrassGroupName), String.join(DOT_DELIMITER, deviceName, KEY));
    }

    @NotNull
    private String getIotCpPropertiesFilename(GreengrassGroupName greengrassGroupName) {
        return String.join("/", getCredentialsDirectoryForGroupName(greengrassGroupName), String.join(DOT_DELIMITER, "iotcp", "properties"));
    }
}
