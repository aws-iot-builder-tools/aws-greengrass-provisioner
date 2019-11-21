package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IamHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

public class BasicIamHelper implements IamHelper {
    private final Logger log = LoggerFactory.getLogger(BasicIamHelper.class);
    @Inject
    IamClient iamClient;
    @Inject
    StsClient stsClient;

    @Inject
    public BasicIamHelper() {
    }

    @Override
    public Optional<Role> getRole(String name) {
        GetRoleRequest getRoleRequest = GetRoleRequest.builder().roleName(name).build();

        return Optional.ofNullable(Try.of(() -> iamClient.getRole(getRoleRequest).role())
                .recover(NoSuchEntityException.class, throwable -> null)
                .get());
    }

    @Override
    public Role createRoleIfNecessary(String name, Optional<String> optionalAssumeRolePolicyDocument) {
        Optional<Role> optionalExistingRole = getRole(name);

        if (optionalExistingRole.isPresent()) {
            log.info("Updating assume role policy for existing role [" + name + "]");
            UpdateAssumeRolePolicyRequest.Builder updateAssumeRolePolicyRequestBuilder = UpdateAssumeRolePolicyRequest.builder();
            updateAssumeRolePolicyRequestBuilder.roleName(name);
            optionalAssumeRolePolicyDocument.ifPresent(updateAssumeRolePolicyRequestBuilder::policyDocument);

            iamClient.updateAssumeRolePolicy(updateAssumeRolePolicyRequestBuilder.build());

            return optionalExistingRole.get();
        }

        log.info("Creating new role [" + name + "]");
        CreateRoleRequest.Builder createRoleRequestBuilder = CreateRoleRequest.builder();
        createRoleRequestBuilder.roleName(name);
        optionalAssumeRolePolicyDocument.ifPresent(createRoleRequestBuilder::assumeRolePolicyDocument);

        CreateRoleResponse createRoleResponse = iamClient.createRole(createRoleRequestBuilder.build());

        return createRoleResponse.role();
    }

    @Override
    public void attachRolePolicies(Role role, Optional<List<String>> optionalManagedPolicyArns) {
        optionalManagedPolicyArns
                .ifPresent(policies -> policies.forEach(policy -> attachRolePolicy(role, policy)));
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
