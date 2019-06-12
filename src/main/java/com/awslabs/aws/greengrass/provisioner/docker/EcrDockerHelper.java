package com.awslabs.aws.greengrass.provisioner.docker;

import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.EcrDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import software.amazon.awssdk.services.ecr.EcrClient;

import javax.inject.Inject;

public class EcrDockerHelper extends AbstractDockerHelper {
    @Inject
    EcrDockerClientProvider ecrDockerClientProvider;

    @Inject
    EcrClient ecrClient;
    @Inject
    ProgressHandler progressHandler;
    @Inject
    ExceptionHelper exceptionHelper;

    @Inject
    public EcrDockerHelper() {
    }

    @Override
    public ExceptionHelper getExceptionHelper() {
        return exceptionHelper;
    }

    @Override
    ProgressHandler getProgressHandler() {
        return progressHandler;
    }

    @Override
    DockerClientProvider getDockerClientProvider() {
        return ecrDockerClientProvider;
    }

    @Override
    protected DockerClient getDockerClient() {
        return ecrDockerClientProvider.get();
    }

    protected EcrClient getEcrClient() {
        return ecrClient;
    }

    @Override
    public String getEcrProxyEndpoint() {
        return ecrDockerClientProvider.getAuthorizationData().proxyEndpoint();
    }
}
