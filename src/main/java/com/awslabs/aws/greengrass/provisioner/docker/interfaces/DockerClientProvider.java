package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.AuthorizationData;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;
import com.amazonaws.util.Base64;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import javax.inject.Provider;
import java.util.List;

public interface DockerClientProvider extends Provider<DockerClient> {
    @Override
    default DockerClient get() {
        // http://stackoverflow.com/questions/40099527/pulling-image-from-amazon-ecr-using-docker-java
        AuthorizationData authorizationData = getAuthorizationData();

        String userPassword = new String(Base64.decode(authorizationData.getAuthorizationToken()));
        String user = userPassword.substring(0, userPassword.indexOf(":"));
        String password = userPassword.substring(userPassword.indexOf(":") + 1);

        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerConfig(null)
                .withDockerHost(getDockerHost())
                .withRegistryUsername(user)
                .withRegistryPassword(password)
                .withRegistryUrl(authorizationData.getProxyEndpoint())
                .build();

        return DockerClientBuilder
                .getInstance(dockerClientConfig)
                .build();
    }

    default AuthorizationData getAuthorizationData() {
        GetAuthorizationTokenRequest getAuthorizationTokenRequest = new GetAuthorizationTokenRequest();
        GetAuthorizationTokenResult getAuthorizationTokenResult = getAmazonECRClient().getAuthorizationToken(getAuthorizationTokenRequest);
        List<AuthorizationData> authorizationDataList = getAuthorizationTokenResult.getAuthorizationData();
        return authorizationDataList.get(0);
    }

    AmazonECRClient getAmazonECRClient();

    String getDockerHost();
}
