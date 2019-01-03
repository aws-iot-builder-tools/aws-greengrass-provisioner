package com.awslabs.aws.greengrass.provisioner.docker;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerHelper;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.Image;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesResponse;
import software.amazon.awssdk.services.ecr.model.RepositoryNotFoundException;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractDockerHelper implements DockerHelper {
    public static String dockerFileDirectory = String.join("/",
            "foundation",
            "docker");
    private Optional<String> ecrRepositoryName;
    private Optional<String> ecrImageName;

    abstract ProgressHandler getProgressHandler();

    abstract DockerClientProvider getDockerClientProvider();

    @Override
    public File getDockerfileForArchitecture(Architecture architecture) {
        String dockerfileName = String.join(".",
                "Dockerfile",
                architecture.toString());

        return new File(String.join("/",
                dockerFileDirectory,
                dockerfileName));
    }

    @Override
    public boolean isDockerAvailable() {
        try {
            listImages();
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to connect to Docker.  Is it running on this host?  Is it listening on the standard Unix socket?");
            log.error("Docker exception message [" + e.getMessage() + "]");

            return false;
        }

        return true;
    }

    private List<Image> listImages() throws DockerException, InterruptedException {
        try (DockerClient dockerClient = getDockerClient()) {
            return dockerClient.listImages(DockerClient.ListImagesParam.allImages());
        }
    }

    protected abstract DockerClient getDockerClient();

    @Override
    public void setEcrRepositoryName(Optional<String> ecrRepositoryName) {
        this.ecrRepositoryName = ecrRepositoryName;
    }

    @Override
    public void createEcrRepositoryIfNecessary() {
        try {
            DescribeRepositoriesResponse describeRepositoriesResponse = getEcrClient().describeRepositories(
                    DescribeRepositoriesRequest.builder()
                            .repositoryNames(ecrRepositoryName.get())
                            .build());

            if (describeRepositoriesResponse.repositories().size() == 1) {
                log.info("ECR repository [" + ecrRepositoryName.get() + "] already exists");
                return;
            }

            log.info("More than one repository found that matched [" + ecrRepositoryName.get() + "], cannot continue");
            throw new RuntimeException("More than one matching ECR repository");
        } catch (RepositoryNotFoundException e) {
            log.info("Creating ECR repository [" + ecrRepositoryName.get() + "]");
            getEcrClient().createRepository(CreateRepositoryRequest.builder()
                    .repositoryName(ecrRepositoryName.get())
                    .build());
        }
    }

    protected abstract EcrClient getEcrClient();

    @Override
    public void setEcrImageName(Optional<String> ecrImageName) {
        this.ecrImageName = ecrImageName;
    }

    @Override
    public String getImageName() {
        return String.join(":", ecrRepositoryName.get(), ecrImageName.get());
    }

    @Override
    public Optional<Image> getImageFromTag(String tag) {
        try {
            List<Image> images = listImages();

            Optional<Image> optionalImage = images.stream()
                    .filter(image -> image.repoTags() != null)
                    .filter(image -> image.repoTags().stream().anyMatch(s -> s.equals(tag)))
                    .findFirst();

            if (optionalImage.isPresent()) {
                log.info("Found tag [" + tag + "] with ID [" + optionalImage.get().id() + "]");
                return optionalImage;
            }

            log.info("Tag [" + tag + "] not found");
            return Optional.empty();
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to list images from Docker [" + e.getMessage() + "]");
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> createContainer(String tag, String groupName) {
        Optional<Image> optionalImage = getImageFromTag(tag);

        if (!optionalImage.isPresent()) {
            return Optional.empty();
        }

        Image image = optionalImage.get();

        try (DockerClient dockerClient = getDockerClient()) {
            return Optional.of(dockerClient.createContainer(ContainerConfig.builder()
                    .image(image.id())
                    .build(), groupName).id());
        } catch (DockerException | InterruptedException e) {
            log.error("Couldn't create container [" + e.getMessage() + "]");
            throw new RuntimeException(e);
        }
    }

    public void createAndStartContainer(String tag, String groupName) {
        Optional<String> optionalContainerId = createContainer(tag, groupName);

        if (!optionalContainerId.isPresent()) {
            log.warn("Container [" + tag + "] not started because it couldn't be created");
            return;
        }

        String containerId = optionalContainerId.get();

        try (DockerClient dockerClient = getDockerClient()) {
            if (!isContainerRunning(groupName, dockerClient)) {
                dockerClient.startContainer(containerId);
            } else {
                log.info("The Docker container for this core is already running locally, the core should be redeploying now");
            }
        } catch (DockerException | InterruptedException e) {
            log.error("Couldn't start container [" + e.getMessage() + "]");
            throw new RuntimeException(e);
        }
    }

    protected Optional<Container> getContainerByName(String groupName, DockerClient dockerClient) throws DockerException, InterruptedException {
        return dockerClient.listContainers(DockerClient.ListContainersParam.allContainers(true)).stream()
                .filter(getContainerPredicate(groupName))
                .findFirst();
    }

    protected boolean isContainerRunning(String groupName, DockerClient dockerClient) throws DockerException, InterruptedException {
        return dockerClient.listContainers(DockerClient.ListContainersParam.withStatusRunning()).stream()
                .anyMatch(getContainerPredicate(groupName));
    }

    protected Predicate<Container> getContainerPredicate(String groupName) {
        String dockerContainerName = String.join("", "/", groupName);
        return container -> container.names().contains(dockerContainerName);
    }

    @Override
    public Optional<Container> getContainerFromImage(String imageId) {
        try {
            List<Container> containers = listContainers();

            Optional<Container> optionalContainer = containers.stream()
                    .filter(container -> container.image().equals(imageId))
                    .findFirst();

            if (optionalContainer.isPresent()) {
                log.info("Found container [" + optionalContainer.get().id() + "] with image ID [" + imageId + "]");
                return optionalContainer;
            }

            log.info("No container running image [" + imageId + "] found");
            return Optional.empty();
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to get container from image [" + e.getMessage() + "]");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dumpImagesInfo() {
        try {
            List<Image> images = listImages();

            String listString = images.stream()
                    .map(image -> image.id() + " [" + image.repoTags().stream().collect(Collectors.joining("|")) + "]")
                    .collect(Collectors.joining(", "));

            log.info("Images [" + listString + "]");
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private List<Container> listContainers() throws DockerException, InterruptedException {
        try (DockerClient dockerClient = getDockerClient()) {
            return dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
        }
    }

    @Override
    public void dumpContainersInfo() {
        try {
            List<Container> containers = listContainers();

            String listString = containers.stream()
                    .map(container -> container.id())
                    .collect(Collectors.joining(", "));

            log.info("Containers [" + listString + "]");
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to list containers [" + e.getMessage() + "]");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stopContainer(String name) {
        Optional<Image> optionalImage = getImageFromTag(name);

        if (!optionalImage.isPresent()) {
            return;
        }

        Image image = optionalImage.get();

        Optional<Container> optionalContainer = getContainerFromImage(image.id());

        if (!optionalContainer.isPresent()) {
            return;
        }

        Container container = optionalContainer.get();

        try (DockerClient dockerClient = getDockerClient()) {
            dockerClient.stopContainer(container.id(), 5);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to stop container [" + e.getMessage() + "]");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void pullImage(String name) {
        try (DockerClient dockerClient = getDockerClient()) {
            dockerClient.pull(name, getDockerClientProvider().getRegistryAuthSupplier().authFor(""), getProgressHandler());
        } catch (DockerException | InterruptedException e) {
            log.error("Couldn't pull image [" + e.getMessage() + "]");
            throw new RuntimeException(e);
        }
    }
}
