package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import org.bouncycastle.util.encoders.Base64;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import javax.inject.Provider;
import java.util.List;

public interface DockerClientProvider extends Provider<DockerClient> {
    @Override
    default DockerClient get() {
        // http://stackoverflow.com/questions/40099527/pulling-image-from-amazon-ecr-using-docker-java
        AuthorizationData authorizationData = getAuthorizationData();

        String userPassword = new String(Base64.decode(authorizationData.authorizationToken()));
        String user = userPassword.substring(0, userPassword.indexOf(":"));
        String password = userPassword.substring(userPassword.indexOf(":") + 1);

        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerConfig(null)
                .withDockerHost(getDockerHost())
                .withRegistryUsername(user)
                .withRegistryPassword(password)
                .withRegistryUrl(authorizationData.proxyEndpoint())
                .build();

        return DockerClientBuilder
                .getInstance(dockerClientConfig)
                .build();
    }

    default AuthorizationData getAuthorizationData() {
        GetAuthorizationTokenRequest getAuthorizationTokenRequest = GetAuthorizationTokenRequest.builder().build();
        GetAuthorizationTokenResponse getAuthorizationTokenResponse = getEcrClient().getAuthorizationToken(getAuthorizationTokenRequest);
        List<AuthorizationData> authorizationDataList = getAuthorizationTokenResponse.authorizationData();
        return authorizationDataList.get(0);
    }

    EcrClient getEcrClient();

    String getDockerHost();
}
