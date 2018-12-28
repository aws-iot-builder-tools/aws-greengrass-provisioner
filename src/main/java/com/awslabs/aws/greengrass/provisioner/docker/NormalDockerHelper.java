package com.awslabs.aws.greengrass.provisioner.docker;

import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.NormalDockerClientProvider;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ecr.EcrClient;

import javax.inject.Inject;

@Slf4j
public class NormalDockerHelper extends AbstractDockerHelper {
    @Inject
    NormalDockerClientProvider normalDockerClientProvider;

    @Inject
    EcrClient ecrClient;
    @Inject
    ProgressHandler progressHandler;

    @Inject
    public NormalDockerHelper() {
    }

    @Override
    ProgressHandler getProgressHandler() {
        return progressHandler;
    }

    @Override
    DockerClientProvider getDockerClientProvider() {
        return normalDockerClientProvider;
    }

    @Override
    protected DockerClient getDockerClient() {
        return normalDockerClientProvider.get();
    }

    protected EcrClient getEcrClient() {
        return ecrClient;
    }

    @Override
    public String getEcrProxyEndpoint() {
        return normalDockerClientProvider.getAuthorizationData().proxyEndpoint();
    }
}
