package com.awslabs.aws.greengrass.provisioner.docker;

import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ecr.EcrClient;

import javax.inject.Inject;

@Slf4j
public class UnixSocketDockerClientProvider implements DockerClientProvider {
    @Inject
    EcrClient ecrClient;

    @Inject
    public UnixSocketDockerClientProvider() {
    }

    @Override
    public EcrClient getEcrClient() {
        return ecrClient;
    }

    @Override
    public String getDockerHost() {
        return "unix:///var/run/docker.sock";
    }
}
