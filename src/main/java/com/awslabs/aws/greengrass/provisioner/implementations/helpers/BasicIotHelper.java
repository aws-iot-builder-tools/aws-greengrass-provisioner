package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.iot.data.ImmutableCertificateId;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.*;

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

    @Override
    public String createThing(String name) {
        CreateThingRequest createThingRequest = CreateThingRequest.builder()
                .thingName(name)
                .build();

        return Try.of(() -> iotClient.createThing(createThingRequest).thingArn())
                .recover(ResourceAlreadyExistsException.class, throwable -> recoverFromResourceAlreadyExistsException(name, throwable))
                .get();
    }

    private String recoverFromResourceAlreadyExistsException(String name, ResourceAlreadyExistsException throwable) {
        if (throwable.getMessage().contains("with different attributes")) {
            log.info("The thing [" + name + "] already exists with different tags/attributes (e.g. immutable or other attributes)");

            DescribeThingRequest describeThingRequest = DescribeThingRequest.builder()
                    .thingName(name)
                    .build();
            return iotClient.describeThing(describeThingRequest).thingArn();
        }

        throw new RuntimeException(throwable);
    }

    private String credentialDirectoryForGroupId(String groupId) {
        return CREDENTIALS + groupId;
    }

    private String createKeysandCertificateFilenameForGroupId(String groupId, String subName) {
        return credentialDirectoryForGroupId(groupId) + "/" + subName + ".createKeysAndCertificate.serialized";
    }

    @Override
    public Optional<KeysAndCertificate> loadKeysAndCertificate(String groupId, String subName) {
        String credentialsDirectory = credentialDirectoryForGroupId(groupId);

        ioHelper.createDirectoryIfNecessary(credentialsDirectory);

        String createKeysAndCertificateFilename = createKeysandCertificateFilenameForGroupId(groupId, subName);

        if (ioHelper.exists(createKeysAndCertificateFilename)) {
            log.info("- Attempting to reuse existing keys.");

            KeysAndCertificate keysAndCertificate = ioHelper.deserializeKeys(ioHelper.readFile(createKeysAndCertificateFilename), jsonHelper);

            if (v2IotHelper.certificateExists(ImmutableCertificateId.builder().id(keysAndCertificate.getCertificateId()).build())) {
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
    public KeysAndCertificate createKeysAndCertificate(String groupId, String subName) {
        String credentialsDirectory = credentialDirectoryForGroupId(groupId);

        ioHelper.createDirectoryIfNecessary(credentialsDirectory);

        String createKeysAndCertificateFilename = createKeysandCertificateFilenameForGroupId(groupId, subName);

        // Let them know that they'll need to re-run the bootstrap script because the core's keys changed
        boolean isCore = subName.equals(DeploymentHelper.CORE_SUB_NAME);
        String supplementalMessage = isCore ? "  If you have an existing deployment for this group you'll need to re-run the bootstrap script since the core certificate ARN will change." : "";
        log.info("- Creating new keys." + supplementalMessage);
        CreateKeysAndCertificateRequest createKeysAndCertificateRequest = CreateKeysAndCertificateRequest.builder()
                .setAsActive(true)
                .build();

        CreateKeysAndCertificateResponse createKeysAndCertificateResponse = iotClient.createKeysAndCertificate(createKeysAndCertificateRequest);

        ioHelper.writeFile(createKeysAndCertificateFilename, ioHelper.serializeKeys(createKeysAndCertificateResponse, jsonHelper).getBytes());

        String deviceName = isCore ? groupId : ggConstants.trimGgdPrefix(subName);
        String privateKeyFilename = BUILD_DIRECTORY + String.join(DOT_DELIMITER, deviceName, PEM, KEY);
        String publicSignedCertificateFilename = BUILD_DIRECTORY + String.join(DOT_DELIMITER, deviceName, PEM, CRT);

        ioHelper.writeFile(privateKeyFilename, createKeysAndCertificateResponse.keyPair().privateKey().getBytes());
        log.info("Device private key written to [" + privateKeyFilename + "]");
        ioHelper.writeFile(publicSignedCertificateFilename, createKeysAndCertificateResponse.certificatePem().getBytes());
        log.info("Device public signed certificate key written to [" + publicSignedCertificateFilename + "]");

        return KeysAndCertificate.from(createKeysAndCertificateResponse);
    }
}
