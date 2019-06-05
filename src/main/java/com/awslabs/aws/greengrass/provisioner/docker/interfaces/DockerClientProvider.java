package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.auth.RegistryAuthSupplier;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.RegistryAuth;
import com.spotify.docker.client.messages.RegistryConfigs;
import org.bouncycastle.util.encoders.Base64;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;

import javax.inject.Provider;

public interface DockerClientProvider extends Provider<DockerClient> {
    default RegistryAuthSupplier getRegistryAuthSupplier() {
        return new RegistryAuthSupplier() {
            @Override
            public RegistryAuth authFor(String imageName) throws DockerException {
                return standardAuth();
            }

            private RegistryAuth standardAuth() {
                AuthorizationData authorizationData = getAuthorizationData();

                String userPassword = new String(Base64.decode(authorizationData.authorizationToken()));
                String user = userPassword.substring(0, userPassword.indexOf(":"));
                String password = userPassword.substring(userPassword.indexOf(":") + 1);

                RegistryAuth registryAuth = RegistryAuth.builder()
                        .username(user)
                        .password(password)
                        .serverAddress(getDockerHost())
                        .build();

                return registryAuth;
            }

            @Override
            public RegistryAuth authForSwarm() throws DockerException {
                throw new RuntimeException("Not implemented");
            }

            @Override
            public RegistryConfigs authForBuild() throws DockerException {
                return RegistryConfigs.builder()
                        .addConfig(getDockerHost(), standardAuth())
                        .build();
            }
        };
    }

    @Override
    default DockerClient get() {
        return DefaultDockerClient.builder()
                .uri(getDockerHost())
                .registryAuthSupplier(getRegistryAuthSupplier())
                .build();
    }

    AuthorizationData getAuthorizationData();

    default String getDockerHost() {
        return "unix:///var/run/docker.sock";
    }
}
