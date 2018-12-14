package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import javax.inject.Inject;
import java.util.List;

public class NormalDockerClientProvider implements DockerClientProvider {
    @Inject
    EcrClient ecrClient;

    @Inject
    public NormalDockerClientProvider() {
    }

    @Override
    public String getRegistryUrl() {
        return getAuthorizationData().proxyEndpoint();
    }

    @Override
    public AuthorizationData getAuthorizationData() {
        GetAuthorizationTokenRequest getAuthorizationTokenRequest = GetAuthorizationTokenRequest.builder().build();
        GetAuthorizationTokenResponse getAuthorizationTokenResponse = ecrClient.getAuthorizationToken(getAuthorizationTokenRequest);
        List<AuthorizationData> authorizationDataList = getAuthorizationTokenResponse.authorizationData();
        return authorizationDataList.get(0);
    }
}
