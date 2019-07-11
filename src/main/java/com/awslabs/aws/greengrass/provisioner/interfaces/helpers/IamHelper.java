package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import software.amazon.awssdk.services.iam.model.Role;

import java.util.Optional;

public interface IamHelper {
    Optional<Role> getRole(String name);

    /**
     * Returns the role object
     *
     * @param name
     * @param assumeRolePolicyDocument
     * @return
     */
    Role createRoleIfNecessary(String name, String assumeRolePolicyDocument);

    void attachRolePolicy(Role role, String policyArn);

    String getAccountId();
}
