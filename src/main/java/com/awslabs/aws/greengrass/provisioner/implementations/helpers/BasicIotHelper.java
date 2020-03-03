package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.*;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Optional;

public class BasicIotHelper implements IotHelper {
    private static final String IOT_DATA_ATS = "iot:Data-ATS";
    private static final String IOT_CREDENTIAL_PROVIDER = "iot:CredentialProvider";
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
    public BasicIotHelper() {
    }

    @Override
    public String getEndpoint() {
        return innerGetEndpoint(IOT_DATA_ATS);
    }

    private String innerGetEndpoint(String endpointType) {
        DescribeEndpointRequest describeEndpointRequest = DescribeEndpointRequest.builder()
                .endpointType(endpointType)
                .build();

        return iotClient.describeEndpoint(describeEndpointRequest).endpointAddress();
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

    private boolean certificateExists(String certificateId) {
        DescribeCertificateRequest describeCertificateRequest = DescribeCertificateRequest.builder()
                .certificateId(certificateId)
                .build();

        return Try.of(() -> iotClient.describeCertificate(describeCertificateRequest) != null)
                .recover(ResourceNotFoundException.class, throwable -> false)
                .get();
    }

    @Override
    public Optional<KeysAndCertificate> loadKeysAndCertificate(String groupId, String subName) {
        String credentialsDirectory = credentialDirectoryForGroupId(groupId);

        ioHelper.createDirectoryIfNecessary(credentialsDirectory);

        String createKeysAndCertificateFilename = createKeysandCertificateFilenameForGroupId(groupId, subName);

        if (ioHelper.exists(createKeysAndCertificateFilename)) {
            log.info("- Attempting to reuse existing keys.");

            KeysAndCertificate keysAndCertificate = ioHelper.deserializeKeys(ioHelper.readFile(createKeysAndCertificateFilename), jsonHelper);

            if (certificateExists(keysAndCertificate.getCertificateId())) {
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

    private boolean policyExists(String name) {
        GetPolicyRequest getPolicyRequest = GetPolicyRequest.builder()
                .policyName(name)
                .build();

        return Try.of(() -> iotClient.getPolicy(getPolicyRequest) != null)
                .recover(ResourceNotFoundException.class, throwable -> false)
                .get();
    }

    @Override
    public void createPolicyIfNecessary(String name, String document) {
        if (policyExists(name)) {
            return;
        }

        CreatePolicyRequest createPolicyRequest = CreatePolicyRequest.builder()
                .policyName(name)
                .policyDocument(document)
                .build();

        iotClient.createPolicy(createPolicyRequest);
    }

    @Override
    public void attachPrincipalPolicy(String policyName, String certificateArn) {
        AttachPolicyRequest attachPolicyRequest = AttachPolicyRequest.builder()
                .policyName(policyName)
                .target(certificateArn)
                .build();

        iotClient.attachPolicy(attachPolicyRequest);
    }

    @Override
    public void attachThingPrincipal(String thingName, String certificateArn) {
        AttachThingPrincipalRequest attachThingPrincipalRequest = AttachThingPrincipalRequest.builder()
                .thingName(thingName)
                .principal(certificateArn)
                .build();

        iotClient.attachThingPrincipal(attachThingPrincipalRequest);
    }

    @Override
    public String getThingPrincipal(String thingName) {
        ListThingPrincipalsRequest listThingPrincipalsRequest = ListThingPrincipalsRequest.builder()
                .thingName(thingName)
                .build();

        ListThingPrincipalsResponse listThingPrincipalsResponse = iotClient.listThingPrincipals(listThingPrincipalsRequest);

        List<String> principals = listThingPrincipalsResponse.principals();

        if ((principals == null) || (principals.size() == 0)) {
            return null;
        }

        return principals.get(0);
    }

    @Override
    public String getThingArn(String thingName) {
        DescribeThingRequest describeThingRequest = DescribeThingRequest.builder()
                .thingName(thingName)
                .build();

        DescribeThingResponse describeThingResponse = iotClient.describeThing(describeThingRequest);

        if (describeThingResponse == null) {
            return null;
        }

        return describeThingResponse.thingArn();
    }

    @Override
    public String getCredentialProviderUrl() {
        return innerGetEndpoint(IOT_CREDENTIAL_PROVIDER);
    }

    @Override
    public CreateRoleAliasResponse createRoleAliasIfNecessary(Role serviceRole, String roleAlias) {
        CreateRoleAliasRequest createRoleAliasRequest = CreateRoleAliasRequest.builder()
                .roleArn(serviceRole.arn())
                .roleAlias(roleAlias)
                .build();

        return Try.of(() -> iotClient.createRoleAlias(createRoleAliasRequest))
                .recover(ResourceAlreadyExistsException.class, throwable -> deleteAndRecreateRoleAlias(roleAlias, createRoleAliasRequest))
                .get();
    }

    private CreateRoleAliasResponse deleteAndRecreateRoleAlias(String roleAlias, CreateRoleAliasRequest createRoleAliasRequest) {
        // Already exists, delete it and try again
        DeleteRoleAliasRequest deleteRoleAliasRequest = DeleteRoleAliasRequest.builder()
                .roleAlias(roleAlias)
                .build();
        iotClient.deleteRoleAlias(deleteRoleAliasRequest);

        return iotClient.createRoleAlias(createRoleAliasRequest);
    }

    @Override
    public String signCsrAndReturnCertificateArn(String csr) {
        if (ioHelper.exists(csr)) {
            // Looks like this is a file path, read the file instead of the CSR as a string
            csr = ioHelper.readFileAsString(new File(csr));
        }

        CreateCertificateFromCsrRequest createCertificateFromCsrRequest = CreateCertificateFromCsrRequest.builder()
                .certificateSigningRequest(csr)
                .setAsActive(true)
                .build();

        CreateCertificateFromCsrResponse certificateFromCsr = iotClient.createCertificateFromCsr(createCertificateFromCsrRequest);
        return certificateFromCsr.certificateArn();
    }

    public String getCertificatePem(String coreCertificateArn) {
        String certificateId = coreCertificateArn.split("/")[1];
        DescribeCertificateRequest describeCertificateRequest = DescribeCertificateRequest.builder()
                .certificateId(certificateId)
                .build();
        DescribeCertificateResponse describeCertificateResponse = iotClient.describeCertificate(describeCertificateRequest);
        return describeCertificateResponse.certificateDescription().certificatePem();
    }
}
