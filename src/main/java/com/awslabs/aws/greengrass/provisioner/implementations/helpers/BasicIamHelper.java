package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IamHelper;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class BasicIamHelper implements IamHelper {
    @Inject
    AmazonIdentityManagementClient amazonIdentityManagementClient;
    @Inject
    AWSSecurityTokenServiceClient awsSecurityTokenServiceClient;

    @Inject
    public BasicIamHelper() {
    }

    private Role getRole(String name) {
        GetRoleRequest getRoleRequest = new GetRoleRequest().withRoleName(name);

        try {
            return amazonIdentityManagementClient.getRole(getRoleRequest).getRole();
        } catch (NoSuchEntityException e) {
            return null;
        }
    }

    @Override
    public Role createRoleIfNecessary(String name, String assumeRolePolicyDocument) {
        Role existingRole = getRole(name);

        if (existingRole != null) {
            log.info("Updating assume role policy for existing role [" + name + "]");
            UpdateAssumeRolePolicyRequest updateAssumeRolePolicyRequest = new UpdateAssumeRolePolicyRequest()
                    .withRoleName(name)
                    .withPolicyDocument(assumeRolePolicyDocument);

            amazonIdentityManagementClient.updateAssumeRolePolicy(updateAssumeRolePolicyRequest);

            return existingRole;
        }

        log.info("Creating new role [" + name + "]");
        CreateRoleRequest createRoleRequest = new CreateRoleRequest()
                .withRoleName(name)
                .withAssumeRolePolicyDocument(assumeRolePolicyDocument);

        CreateRoleResult createRoleResult = amazonIdentityManagementClient.createRole(createRoleRequest);

        return createRoleResult.getRole();
    }

    @Override
    public void attachRolePolicy(Role role, String policyArn) {
        AttachRolePolicyRequest attachRolePolicyRequest = new AttachRolePolicyRequest()
                .withRoleName(role.getRoleName())
                .withPolicyArn(policyArn);

        amazonIdentityManagementClient.attachRolePolicy(attachRolePolicyRequest);
    }

    @Override
    public String getAccountId() {
        return awsSecurityTokenServiceClient.getCallerIdentity(new GetCallerIdentityRequest()).getAccount();
    }
}
