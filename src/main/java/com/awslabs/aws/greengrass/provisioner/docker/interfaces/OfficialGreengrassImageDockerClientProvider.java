package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class OfficialGreengrassImageDockerClientProvider implements DockerClientProvider {
    @Inject
    GGConstants ggConstants;

    @Inject
    public OfficialGreengrassImageDockerClientProvider() {
    }

    @Override
    public AuthorizationData getAuthorizationData() {
        Optional<List<String>> optionalRegistryIds = Optional.of(Arrays.asList(ggConstants.getOfficialGreengrassAccountId()));
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
