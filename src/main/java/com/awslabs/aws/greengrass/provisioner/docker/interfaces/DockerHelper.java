package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.Image;

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

    Optional<Image> getImageFromTag(String tag);

    /**
     * Creates a container, if necessary, and returns the container ID
     *
     * @param tag
     * @param groupName
     * @return the container ID or empty if the container could not be created or found
     */
    Optional<String> createContainer(String tag, String groupName);

    Optional<Container> getContainerFromImage(String imageId);

    void dumpImagesInfo();

    void dumpContainersInfo();

    void stopContainer(String name);

    void pullImage(String name);
}
