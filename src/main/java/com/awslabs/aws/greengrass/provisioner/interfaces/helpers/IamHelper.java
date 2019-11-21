package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import software.amazon.awssdk.services.iam.model.Role;

import java.util.List;
import java.util.Optional;

public interface IamHelper {
    Optional<Role> getRole(String name);

    /**
     * Returns the role object
     *
     * @param name
     * @param optionalAssumeRolePolicyDocument
     * @return
     */
    Role createRoleIfNecessary(String name, Optional<String> optionalAssumeRolePolicyDocument);

    void attachRolePolicies(Role role, Optional<List<String>> optionalManagedPolicyArns);

    void attachRolePolicy(Role role, String policyArn);

    String getAccountId();
}
