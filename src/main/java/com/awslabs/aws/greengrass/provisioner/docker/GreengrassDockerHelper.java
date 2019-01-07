package com.awslabs.aws.greengrass.provisioner.docker;

import com.awslabs.aws.greengrass.provisioner.docker.interfaces.DockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.docker.interfaces.GreengrassDockerClientProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.Image;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class GreengrassDockerHelper extends AbstractDockerHelper {
    @Inject
    GreengrassDockerClientProvider greengrassDockerClientProvider;
    @Inject
    ProgressHandler progressHandler;
    @Inject
    GGConstants ggConstants;
    @Inject
    GGVariables ggVariables;
    @Inject
    IoHelper ioHelper;

    @Inject
    public GreengrassDockerHelper() {
    }

    @Override
    ProgressHandler getProgressHandler() {
        return progressHandler;
    }

    @Override
    DockerClientProvider getDockerClientProvider() {
        return greengrassDockerClientProvider;
    }

    @Override
    protected DockerClient getDockerClient() {
        return greengrassDockerClientProvider.get();
    }

    @Override
    protected EcrClient getEcrClient() {
        return EcrClient.builder()
                .region(Region.US_WEST_2)
                .build();
    }

    @Override
    public Optional<String> createContainer(String tag, String groupName) {
        Optional<Image> optionalImage = getImageFromTag(tag);

        if (!optionalImage.isPresent()) {
            return Optional.empty();
        }

        Image image = optionalImage.get();

        String absoluteCertsPath = String.join("/", "", "greengrass", ggConstants.getCertsDirectoryPrefix());
        String absoluteConfigPath = String.join("/", "", "greengrass", ggConstants.getConfigDirectoryPrefix());

        Path tempDirectory = Try.of(() -> {
            Path innerTempDirectory = Files.createTempDirectory(groupName);

            Path localCertsPath = innerTempDirectory.resolve(ggConstants.getCertsDirectoryPrefix());
            Path localConfigPath = innerTempDirectory.resolve(ggConstants.getConfigDirectoryPrefix());

            Files.createDirectory(localCertsPath);
            Files.createDirectory(localConfigPath);

            String coreCertFilename = String.join("/",
                    ggConstants.getCertsDirectoryPrefix(),
                    ggConstants.getCorePublicCertificateName());

            String coreKeyFilename = String.join("/",
                    ggConstants.getCertsDirectoryPrefix(),
                    ggConstants.getCorePrivateKeyName());

            String rootCaFilename = String.join("/",
                    ggConstants.getCertsDirectoryPrefix(),
                    ggConstants.getRootCaName());
            String configJsonFilename = String.join("/",
                    ggConstants.getConfigDirectoryPrefix(),
                    ggConstants.getConfigFileName());

            Optional<InputStream> coreCertStream = ioHelper.extractTar(ggVariables.getOemArchiveName(groupName), coreCertFilename);
            Optional<InputStream> coreKeyStream = ioHelper.extractTar(ggVariables.getOemArchiveName(groupName), coreKeyFilename);
            Optional<InputStream> rootCaStream = ioHelper.extractTar(ggVariables.getOemArchiveName(groupName), rootCaFilename);
            Optional<InputStream> configJsonStream = ioHelper.extractTar(ggVariables.getOemArchiveName(groupName), configJsonFilename);

            List<String> errors = new ArrayList<>();

            if (!coreCertStream.isPresent()) {
                errors.add("Couldn't extract core certificate from the OEM file");
            }

            if (!coreKeyStream.isPresent()) {
                errors.add("Couldn't extract core private key from the OEM file");
            }

            if (!rootCaStream.isPresent()) {
                errors.add("Couldn't extract the root CA from the OEM file");
            }

            if (!configJsonStream.isPresent()) {
                errors.add("Couldn't extract the config.json from the OEM file");
            }

            if (errors.size() != 0) {
                errors.stream()
                        .forEach(error -> log.error(error));
                throw new RuntimeException("OEM extraction failed");
            }

            FileUtils.copyInputStreamToFile(coreCertStream.get(), innerTempDirectory.resolve(coreCertFilename).toFile());
            FileUtils.copyInputStreamToFile(coreKeyStream.get(), innerTempDirectory.resolve(coreKeyFilename).toFile());
            FileUtils.copyInputStreamToFile(rootCaStream.get(), innerTempDirectory.resolve(rootCaFilename).toFile());
            FileUtils.copyInputStreamToFile(configJsonStream.get(), innerTempDirectory.resolve(configJsonFilename).toFile());

            return innerTempDirectory;
        }).recover(IOException.class, throwable -> {
            printCouldNotCreateTemporaryCredentialsAndThrow(throwable);
            return null;
        })
                .get();

        return Try.withResources(this::getDockerClient)
                .of(dockerClient -> {
                    // Is there any existing container with the group name?
                    Optional<Container> optionalContainer = getContainerByName(groupName, dockerClient);

                    if (optionalContainer.isPresent()) {
                        Container container = optionalContainer.get();
                        String containerId = container.id();

                        return Optional.of(containerId);
                    }

                    ContainerCreation containerCreation = dockerClient.createContainer(ContainerConfig.builder()
                            .image(image.id())
                            .entrypoint("/greengrass-entrypoint.sh")
                            .exposedPorts("8883")
                            .build(), groupName);

                    // Copy the certs to the container
                    dockerClient.copyToContainer(tempDirectory.resolve(ggConstants.getCertsDirectoryPrefix()),
                            containerCreation.id(),
                            absoluteCertsPath);

                    // Copy the config to the container
                    dockerClient.copyToContainer(tempDirectory.resolve(ggConstants.getConfigDirectoryPrefix()),
                            containerCreation.id(),
                            absoluteConfigPath);

                    return Optional.of(containerCreation.id());
                })
                .recover(DockerException.class, this::printCouldNotCreateContainerAndThrow)
                .recover(InterruptedException.class, this::printCouldNotCreateContainerAndThrow)
                .recover(IOException.class, this::printCouldNotCopyFilesToContainerAndThrow)
                .get();
    }

    private Optional<String> printCouldNotCreateContainerAndThrow(Throwable throwable) {
        log.error("Couldn't create container [" + throwable.getMessage() + "]");
        throw new RuntimeException(throwable);
    }

    private Optional<String> printCouldNotCopyFilesToContainerAndThrow(Throwable throwable) {
        log.error("Couldn't copy files to container [" + throwable.getMessage() + "]");
        throw new RuntimeException(throwable);
    }

    private Optional<String> printCouldNotCreateTemporaryCredentialsAndThrow(Throwable throwable) {
        log.error("Couldn't create temporary path for credentials");
        throw new RuntimeException(throwable);
    }

    @Override
    public String getEcrProxyEndpoint() {
        return "https://216483018798.dkr.ecr.us-west-2.amazonaws.com";
    }
}
