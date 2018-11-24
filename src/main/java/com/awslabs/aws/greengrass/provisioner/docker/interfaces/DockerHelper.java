package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;

import java.io.File;
import java.util.Optional;

public interface DockerHelper {
    File getDockerfileForArchitecture(Architecture architecture);

    boolean isDockerAvailable();

    void setEcrRepositoryName(Optional<String> ecrRepositoryName);

    void createEcrRepositoryIfNecessary();

    void setEcrImageName(Optional<String> ecrImageName);

    String getImageName();

    String getEcrProxyEndpoint();
}
