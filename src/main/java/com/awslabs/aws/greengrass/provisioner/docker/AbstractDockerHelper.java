package com.awslabs.aws.greengrass.provisioner.docker;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.Image;
import io.vavr.control.Try;
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

import static io.vavr.API.*;
import static io.vavr.Predicates.instanceOf;

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
        return Try.of(() -> listImages() != null)
                .recover(DockerException.class, this::printDockerNotAvailableErrorAndReturnFalse)
                .recover(InterruptedException.class, this::printDockerNotAvailableErrorAndReturnFalse)
                .get();
    }

    private boolean printDockerNotAvailableErrorAndReturnFalse(Throwable throwable) {
        log.error("Failed to connect to Docker.  Is it running on this host?  Is it listening on the standard Unix socket?");
        log.error("Docker exception message [" + throwable.getMessage() + "]");

        return false;
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
        Try.of(() -> describeRepositories())
                .onFailure(throwable -> Match(throwable).of(
                        Case($(instanceOf(RepositoryNotFoundException.class)), ignore -> createEcrRepository()),
                        Case($(), this::rethrowAsRuntimeException)))
                .get();
    }

    private Void describeRepositories() {
        DescribeRepositoriesResponse describeRepositoriesResponse = getEcrClient().describeRepositories(
                DescribeRepositoriesRequest.builder()
                        .repositoryNames(ecrRepositoryName.get())
                        .build());

        if (describeRepositoriesResponse.repositories().size() == 1) {
            log.info("ECR repository [" + ecrRepositoryName.get() + "] already exists");
            return null;
        }

        log.info("More than one repository found that matched [" + ecrRepositoryName.get() + "], cannot continue");

        throw new RuntimeException("More than one matching ECR repository");
    }

    private Void rethrowAsRuntimeException(Throwable throwable) {
        getExceptionHelper().rethrowAsRuntimeException(throwable);

        return null;
    }

    protected abstract ExceptionHelper getExceptionHelper();

    public Void createEcrRepository() {
        log.info("Creating ECR repository [" + ecrRepositoryName.get() + "]");
        getEcrClient().createRepository(CreateRepositoryRequest.builder()
                .repositoryName(ecrRepositoryName.get())
                .build());

        return null;
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
        Optional<Image> optionalImage = Try.of(() -> listImages().stream()
                .filter(image -> image.repoTags() != null)
                .filter(image -> image.repoTags().stream().anyMatch(s -> s.equals(tag)))
                .findFirst())
                .recover(DockerException.class, this::printFailedToListImagesFromDockerAndReturnEmpty)
                .recover(InterruptedException.class, this::printFailedToListImagesFromDockerAndReturnEmpty)
                .get();

        optionalImage.ifPresent(image -> log.info("Found tag [" + tag + "] with ID [" + optionalImage.get().id() + "]"));

        if (!optionalImage.isPresent()) {
            log.info("Tag [" + tag + "] not found");
        }

        return optionalImage;
    }

    private Optional<Image> printFailedToListImagesFromDockerAndReturnEmpty(Throwable throwable) {
        log.error("Failed to list images from Docker [" + throwable.getMessage() + "]");
        return Optional.empty();
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
        Optional<Container> optionalContainer = Try.of(() -> listContainers().stream()
                .filter(container -> container.image().equals(imageId))
                .findFirst())
                .recover(DockerException.class, this::printFailedToGetContainerFromImageAndThrow)
                .recover(InterruptedException.class, this::printFailedToGetContainerFromImageAndThrow)
                .get();

        optionalContainer.ifPresent(container -> log.info("Found container [" + container.id() + "] with image ID [" + imageId + "]"));

        if (!optionalContainer.isPresent()) {
            log.info("No container running image [" + imageId + "] found");
        }

        return optionalContainer;
    }

    private Optional<Container> printFailedToGetContainerFromImageAndThrow(Throwable throwable) {
        log.error("Failed to get container from image [" + throwable.getMessage() + "]");
        throw new RuntimeException(throwable);
    }

    @Override
    public void dumpImagesInfo() {
        Try.of(() -> listImages().stream()
                .map(image -> image.id() + " [" + image.repoTags().stream().collect(Collectors.joining("|")) + "]")
                .collect(Collectors.joining(", ")))
                .onSuccess(string -> log.info("Images [" + string + "]"))
                .onFailure(Throwable::printStackTrace);
    }

    private List<Container> listContainers() {
        return Try.withResources(this::getDockerClient)
                .of(dockerClient -> dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()))
                .get();
    }

    @Override
    public void dumpContainersInfo() {
        Try.of(() -> listContainers().stream()
                .map(container -> container.id())
                .collect(Collectors.joining(", ")))
                .onSuccess(string -> log.info("Containers [" + string + "]"))
                .recover(DockerException.class, this::printFailedToListContainersAndThrow)
                .recover(InterruptedException.class, this::printFailedToListContainersAndThrow)
                .get();
    }

    private String printFailedToListContainersAndThrow(Throwable throwable) {
        log.error("Failed to list containers [" + throwable.getMessage() + "]");
        throw new RuntimeException(throwable);
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

        Try.withResources(this::getDockerClient)
                .of(dockerClient -> stopContainer(container, dockerClient))
                .onFailure(throwable -> Match(throwable).of(
                        Case($(instanceOf(DockerException.class)), this::printFailedToStopContainerAndThrow),
                        Case($(instanceOf(InterruptedException.class)), this::printFailedToStopContainerAndThrow),
                        Case($(), this::rethrowAsRuntimeException)))
                .get();
    }

    public Void stopContainer(Container container, DockerClient dockerClient) throws DockerException, InterruptedException {
        dockerClient.stopContainer(container.id(), 5);
        return null;
    }

    private Void printFailedToStopContainerAndThrow(Throwable throwable) {
        log.error("Failed to stop container [" + throwable.getMessage() + "]");
        throw new RuntimeException(throwable);
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
