package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class GreengrassDockerClientProvider implements DockerClientProvider {
    @Inject
    public GreengrassDockerClientProvider() {
    }

    @Override
    public String getRegistryUrl() {
        return "https://216483018798.dkr.ecr.us-west-2.amazonaws.com";
    }

    @Override
    public AuthorizationData getAuthorizationData() {
        Optional<List<String>> optionalRegistryIds = Optional.of(Arrays.asList(new String[]{"216483018798"}));
        GetAuthorizationTokenRequest.Builder getAuthorizationTokenRequestBuilder = GetAuthorizationTokenRequest.builder();
        optionalRegistryIds.ifPresent(getAuthorizationTokenRequestBuilder::registryIds);
        GetAuthorizationTokenRequest getAuthorizationTokenRequest = getAuthorizationTokenRequestBuilder.build();
        GetAuthorizationTokenResponse getAuthorizationTokenResponse = getEcrClient().getAuthorizationToken(getAuthorizationTokenRequest);
        List<AuthorizationData> authorizationDataList = getAuthorizationTokenResponse.authorizationData();
        return authorizationDataList.get(0);
    }

    private EcrClient getEcrClient() {
        return EcrClient.builder()
                .region(Region.US_WEST_2)
                .build();
    }
}
