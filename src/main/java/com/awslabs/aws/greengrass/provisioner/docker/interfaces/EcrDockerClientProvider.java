package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import javax.inject.Inject;
import java.util.List;

public class EcrDockerClientProvider implements DockerClientProvider {
    @Inject
    EcrClient ecrClient;

    @Inject
    public EcrDockerClientProvider() {
    }

    @Override
    public AuthorizationData getAuthorizationData() {
        GetAuthorizationTokenRequest getAuthorizationTokenRequest = GetAuthorizationTokenRequest.builder().build();
        GetAuthorizationTokenResponse getAuthorizationTokenResponse = ecrClient.getAuthorizationToken(getAuthorizationTokenRequest);
        List<AuthorizationData> authorizationDataList = getAuthorizationTokenResponse.authorizationData();
        return authorizationDataList.get(0);
    }
}
