package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.KeysAndCertificate;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iot.model.CreateRoleAliasResponse;

public interface IotHelper {
    String getEndpoint();

    String createThing(String name);

    KeysAndCertificate createOrLoadKeysAndCertificate(String groupId, String subName);

    void createPolicyIfNecessary(String name, String document);

    void attachPrincipalPolicy(String policyName, String certificateArn);

    void attachThingPrincipal(String thingName, String certificateArn);

    /**
     * Returns the ARN of the principal attached to the specified thing if there is exactly one principal attached.
     * Otherwise it returns null.
     *
     * @param thingArn
     * @return
     */
    String getThingPrincipal(String thingArn);

    String getThingArn(String connectedShadowThingName);

    String getCredentialProviderUrl();

    CreateRoleAliasResponse createRoleAliasIfNecessary(Role serviceRole, String roleAlias);
}
