package com.awslabs.aws.greengrass.provisioner.docker;

import com.amazonaws.services.ecr.AmazonECRClient;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class UnixSocketDockerClientProvider implements DockerClientProvider {
    @Inject
    AmazonECRClient amazonECRClient;

    @Inject
    public UnixSocketDockerClientProvider() {
    }

    @Override
    public AmazonECRClient getAmazonECRClient() {
        return amazonECRClient;
    }

    @Override
    public String getDockerHost() {
        return "unix:///var/run/docker.sock";
    }
}
