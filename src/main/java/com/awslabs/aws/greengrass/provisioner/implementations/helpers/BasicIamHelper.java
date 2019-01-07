package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IamHelper;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

import javax.inject.Inject;

@Slf4j
public class BasicIamHelper implements IamHelper {
    @Inject
    IamClient iamClient;
    @Inject
    StsClient stsClient;

    @Inject
    public BasicIamHelper() {
    }

    private Role getRole(String name) {
        GetRoleRequest getRoleRequest = GetRoleRequest.builder().roleName(name).build();

        return Try.of(() -> iamClient.getRole(getRoleRequest).role())
                .recover(NoSuchEntityException.class, throwable -> null)
                .get();
    }

    @Override
    public Role createRoleIfNecessary(String name, String assumeRolePolicyDocument) {
        Role existingRole = getRole(name);

        if (existingRole != null) {
            log.info("Updating assume role policy for existing role [" + name + "]");
            UpdateAssumeRolePolicyRequest updateAssumeRolePolicyRequest = UpdateAssumeRolePolicyRequest.builder()
                    .roleName(name)
                    .policyDocument(assumeRolePolicyDocument)
                    .build();

            iamClient.updateAssumeRolePolicy(updateAssumeRolePolicyRequest);

            return existingRole;
        }

        log.info("Creating new role [" + name + "]");
        CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
                .roleName(name)
                .assumeRolePolicyDocument(assumeRolePolicyDocument)
                .build();

        CreateRoleResponse createRoleResponse = iamClient.createRole(createRoleRequest);

        return createRoleResponse.role();
    }

    @Override
    public void attachRolePolicy(Role role, String policyArn) {
        AttachRolePolicyRequest attachRolePolicyRequest = AttachRolePolicyRequest.builder()
                .roleName(role.roleName())
                .policyArn(policyArn)
                .build();

        iamClient.attachRolePolicy(attachRolePolicyRequest);
    }

    @Override
    public String getAccountId() {
        return stsClient.getCallerIdentity(GetCallerIdentityRequest.builder().build()).account();
    }
}
