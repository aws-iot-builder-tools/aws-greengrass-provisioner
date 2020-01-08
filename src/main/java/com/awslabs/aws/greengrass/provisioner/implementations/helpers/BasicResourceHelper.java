package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.*;

import javax.inject.Inject;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.io.Resources.getResource;

public class BasicResourceHelper implements ResourceHelper {
    private final Logger log = LoggerFactory.getLogger(BasicGreengrassHelper.class);

    @Inject
    public BasicResourceHelper() {
    }

    private String inputStreamToString(InputStream inputStream) {
        String string = new Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();

        return string;
    }

    @Override
    public InputStream getResourceAsStream(String resourcePath) {
        return Try.of(() -> getResource(resourcePath).openStream()).get();
    }

    @Override
    public InputStream getFileOrResourceAsStream(String sourcePath) {
        File resource = new File(sourcePath);

        if (!resource.exists()) {
            return getResourceAsStream(sourcePath);
        }

        return Try.of(() -> new FileInputStream(resource))
                .recover(FileNotFoundException.class, exception -> null)
                .get();
    }

    @Override
    public String resourceToString(String filename) {
        return inputStreamToString(getResourceAsStream(filename));
    }

    @Override
    public String resourceToTempFile(String filename) throws IOException {
        InputStream inputStream = getResourceAsStream(filename);

        if (inputStream == null) {
            throw new RuntimeException("Cannot get resource [" + filename + "] from JAR file.");
        }

        int readBytes;
        byte[] buffer = new byte[8192];

        File tempFile = File.createTempFile("temp", "jar");
        tempFile.deleteOnExit();
        FileOutputStream fileOutputStream = new FileOutputStream(tempFile);

        while ((readBytes = inputStream.read(buffer)) > 0) {
            fileOutputStream.write(buffer, 0, readBytes);
        }

        return tempFile.toString();
    }

    @Override
    public void validateResourceDefinitionVersion(ResourceDefinitionVersion resourceDefinitionVersion) {
        List<Resource> resourceList = resourceDefinitionVersion.resources();

        List<LocalDeviceResourceData> localDeviceResources = resourceList.stream()
                .map(res -> res.resourceDataContainer().localDeviceResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateLocalDeviceSourcePaths(localDeviceResources);

        List<LocalVolumeResourceData> localVolumeResources = resourceList.stream()
                .map(res -> res.resourceDataContainer().localVolumeResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateLocalVolumeSourcePaths(localVolumeResources);

        List<S3MachineLearningModelResourceData> localS3Resources = resourceList.stream()
                .map(res -> res.resourceDataContainer().s3MachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateS3DestinationPaths(localS3Resources);

        List<SageMakerMachineLearningModelResourceData> localSageMakerResources = resourceList.stream()
                .map(res -> res.resourceDataContainer().sageMakerMachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateSageMakerDestinationPaths(localSageMakerResources);

        List<SecretsManagerSecretResourceData> localSecretsManagerResources = resourceList.stream()
                .map(res -> res.resourceDataContainer().secretsManagerSecretResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateSecretsManagerSecrets(localSecretsManagerResources);
    }

    private void disallowDuplicateSecretsManagerSecrets(List<SecretsManagerSecretResourceData> resources) {
        List<String> secretArns = resources.stream()
                .map(SecretsManagerSecretResourceData::arn)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(secretArns);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local secrets manager secrets defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateLocalDeviceSourcePaths(List<LocalDeviceResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(LocalDeviceResourceData::sourcePath)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local device resource source paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateLocalVolumeSourcePaths(List<LocalVolumeResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(LocalVolumeResourceData::sourcePath)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local volume resource source paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateSageMakerDestinationPaths(List<SageMakerMachineLearningModelResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(SageMakerMachineLearningModelResourceData::destinationPath)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local SageMaker resource destination paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateS3DestinationPaths(List<S3MachineLearningModelResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(S3MachineLearningModelResourceData::destinationPath)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findDuplicates(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> "\"" + string + "\"")
                    .collect(Collectors.joining(", "));

            log.error("Duplicate local S3 resource destination paths defined [" + duplicatesString + "].  Greengrass will not accept this configuration.");
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private Optional<List<String>> findDuplicates(List<String> inputList) {
        List<String> outputList = new ArrayList<>(inputList);
        Set<String> deduplicatedList = new HashSet<>(inputList);

        if (outputList.size() != deduplicatedList.size()) {
            deduplicatedList
                    .forEach(outputList::remove);

            return Optional.of(outputList);
        }

        return Optional.empty();
    }
}
