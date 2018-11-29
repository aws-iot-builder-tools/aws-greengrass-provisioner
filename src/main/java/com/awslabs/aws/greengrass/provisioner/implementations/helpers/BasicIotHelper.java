package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.*;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeploymentHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;


@Slf4j
public class BasicIotHelper implements IotHelper {
    public static final String CREDENTIALS = "credentials/";
    @Inject
    AWSIotClient awsIotClient;
    @Getter(lazy = true)
    private final String endpoint = describeEndpoint();
    @Inject
    IoHelper ioHelper;
    @Inject
    GGConstants ggConstants;

    @Inject
    public BasicIotHelper() {
    }

    private String describeEndpoint() {
        return awsIotClient.describeEndpoint(new DescribeEndpointRequest()).getEndpointAddress();
    }

    @Override
    public String createThing(String name) {
        CreateThingRequest createThingRequest = new CreateThingRequest()
                .withThingName(name);

        try {
            return awsIotClient.createThing(createThingRequest).getThingArn();
        } catch (ResourceAlreadyExistsException e) {
            if (e.getMessage().contains("with different tags")) {
                log.info("The thing [" + name + "] already exists with different tags/attributes (e.g. immutable or other attributes)");

                DescribeThingRequest describeThingRequest = new DescribeThingRequest()
                        .withThingName(name);
                return awsIotClient.describeThing(describeThingRequest).getThingArn();
            }

            throw new UnsupportedOperationException(e);
        }
    }

    private String credentialDirectoryForGroupId(String groupId) {
        return CREDENTIALS + groupId;
    }

    private String createKeysandCertificateFilenameForGroupId(String groupId, String subName) {
        return credentialDirectoryForGroupId(groupId) + "/" + subName + ".createKeysAndCertificate.serialized";
    }

    private boolean certificateExists(String certificateId) {
        DescribeCertificateRequest describeCertificateRequest = new DescribeCertificateRequest()
                .withCertificateId(certificateId);

        try {
            awsIotClient.describeCertificate(describeCertificateRequest);
        } catch (ResourceNotFoundException e) {
            return false;
        }

        return true;
    }

    @Override
    public CreateKeysAndCertificateResult createOrLoadKeysAndCertificate(String groupId, String subName) {
        String credentialsDirectory = credentialDirectoryForGroupId(groupId);

        ioHelper.createDirectoryIfNecessary(credentialsDirectory);

        String createKeysAndCertificateFilename = createKeysandCertificateFilenameForGroupId(groupId, subName);

        if (ioHelper.exists(createKeysAndCertificateFilename)) {
            log.info("- Attempting to reuse existing keys.");

            CreateKeysAndCertificateResult createKeysAndCertificateResult = (CreateKeysAndCertificateResult) ioHelper.deserializeObject(ioHelper.readFile(createKeysAndCertificateFilename));

            if (certificateExists(createKeysAndCertificateResult.getCertificateId())) {
                log.info("- Reusing existing keys.");
                return createKeysAndCertificateResult;
            } else {
                log.warn("- Existing certificate is not in AWS IoT.  It may have been deleted.");
            }
        }

        // Let them know that they'll need to re-run the bootstrap script because the core's keys changed
        boolean isCore = subName.equals(DeploymentHelper.CORE_SUB_NAME);
        String supplementalMessage = isCore ? "  If you have an existing deployment for this group you'll need to re-run the bootstrap script since the core certificate ARN will change." : "";
        log.info("- Keys not found, creating new keys." + supplementalMessage);
        CreateKeysAndCertificateRequest createKeysAndCertificateRequest = new CreateKeysAndCertificateRequest()
                .withSetAsActive(true);

        CreateKeysAndCertificateResult createKeysAndCertificateResult = awsIotClient.createKeysAndCertificate(createKeysAndCertificateRequest);

        ioHelper.writeFile(createKeysAndCertificateFilename, ioHelper.serializeObject(createKeysAndCertificateResult));

        String deviceName = isCore ? groupId : ggConstants.trimGgdPrefix(subName);
        String privateKeyFilename = "build/" + String.join(".", deviceName, "pem", "key");
        String publicSignedCertificateFilename = "build/" + String.join(".", deviceName, "pem", "crt");

        ioHelper.writeFile(privateKeyFilename, createKeysAndCertificateResult.getKeyPair().getPrivateKey().getBytes());
        log.info("Device private key written to [" + privateKeyFilename + "]");
        ioHelper.writeFile(publicSignedCertificateFilename, createKeysAndCertificateResult.getCertificatePem().getBytes());
        log.info("Device public signed certificate key written to [" + publicSignedCertificateFilename + "]");

        return createKeysAndCertificateResult;
    }

    private boolean policyExists(String name) {
        GetPolicyRequest getPolicyRequest = new GetPolicyRequest()
                .withPolicyName(name);

        try {
            awsIotClient.getPolicy(getPolicyRequest);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    @Override
    public void createPolicyIfNecessary(String name, String document) {
        if (policyExists(name)) {
            return;
        }

        CreatePolicyRequest createPolicyRequest = new CreatePolicyRequest()
                .withPolicyName(name)
                .withPolicyDocument(document);

        awsIotClient.createPolicy(createPolicyRequest);
    }

    @Override
    public void attachPrincipalPolicy(String policyName, String certificateArn) {
        AttachPolicyRequest attachPolicyRequest = new AttachPolicyRequest()
                .withPolicyName(policyName)
                .withTarget(certificateArn);

        awsIotClient.attachPolicy(attachPolicyRequest);
    }

    @Override
    public void attachThingPrincipal(String thingName, String certificateArn) {
        AttachThingPrincipalRequest attachThingPrincipalRequest = new AttachThingPrincipalRequest()
                .withThingName(thingName)
                .withPrincipal(certificateArn);

        awsIotClient.attachThingPrincipal(attachThingPrincipalRequest);
    }

    @Override
    public String getThingPrincipal(String thingName) {
        ListThingPrincipalsRequest listThingPrincipalsRequest = new ListThingPrincipalsRequest()
                .withThingName(thingName);

        ListThingPrincipalsResult listThingPrincipalsResult = awsIotClient.listThingPrincipals(listThingPrincipalsRequest);

        List<String> principals = listThingPrincipalsResult.getPrincipals();

        if ((principals == null) || (principals.size() == 0)) {
            return null;
        }

        return principals.get(0);
    }

    @Override
    public String getThingArn(String thingName) {
        DescribeThingRequest describeThingRequest = new DescribeThingRequest()
                .withThingName(thingName);

        DescribeThingResult describeThingResult = awsIotClient.describeThing(describeThingRequest);

        if (describeThingResult == null) {
            return null;
        }

        return describeThingResult.getThingArn();
    }

    @Override
    public String getCredentialProviderUrl() {
        DescribeEndpointRequest describeEndpointRequest = new DescribeEndpointRequest()
                .withEndpointType("iot:CredentialProvider");

        return awsIotClient.describeEndpoint(describeEndpointRequest).getEndpointAddress();
    }

    @Override
    public CreateRoleAliasResult createRoleAliasIfNecessary(Role serviceRole, String roleAlias) {
        CreateRoleAliasRequest createRoleAliasRequest = new CreateRoleAliasRequest()
                .withRoleArn(serviceRole.getArn())
                .withRoleAlias(roleAlias);

        try {
            return awsIotClient.createRoleAlias(createRoleAliasRequest);
        } catch (ResourceAlreadyExistsException e) {
            // Already exists, delete so we can try
            DeleteRoleAliasRequest deleteRoleAliasRequest = new DeleteRoleAliasRequest()
                    .withRoleAlias(roleAlias);
            awsIotClient.deleteRoleAlias(deleteRoleAliasRequest);
        }

        return awsIotClient.createRoleAlias(createRoleAliasRequest);
    }
}
