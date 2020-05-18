package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GreengrassResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.*;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class BasicGreengrassResourceHelper implements GreengrassResourceHelper {
    private final Logger log = LoggerFactory.getLogger(BasicGreengrassHelper.class);

    @Inject
    public BasicGreengrassResourceHelper() {
    }

    @Override
    public void validateResourceDefinitionVersion(ResourceDefinitionVersion resourceDefinitionVersion) {
        List<Resource> resources = resourceDefinitionVersion.resources();

        List<LocalDeviceResourceData> localDeviceResources = resources.stream()
                .map(res -> res.resourceDataContainer().localDeviceResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateDeviceSourcePaths(localDeviceResources);
        disallowDeviceSourcePathsNotInDev(localDeviceResources);

        List<LocalVolumeResourceData> localVolumeResources = resources.stream()
                .map(res -> res.resourceDataContainer().localVolumeResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateVolumeSourcePathsWithDifferentDestinationPaths(localVolumeResources);

        List<S3MachineLearningModelResourceData> localS3Resources = resources.stream()
                .map(res -> res.resourceDataContainer().s3MachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateS3DestinationPaths(localS3Resources);

        List<SageMakerMachineLearningModelResourceData> localSageMakerResources = resources.stream()
                .map(res -> res.resourceDataContainer().sageMakerMachineLearningModelResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateSageMakerDestinationPaths(localSageMakerResources);

        List<SecretsManagerSecretResourceData> localSecretsManagerResources = resources.stream()
                .map(res -> res.resourceDataContainer().secretsManagerSecretResourceData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        disallowDuplicateSecretsManagerSecrets(localSecretsManagerResources);
    }

    @Override
    public <R> List<R> flatMapResources(List<FunctionConf> functionConfs, java.util.function.Function<FunctionConf, List<R>> method) {
        return functionConfs.stream()
                .map(method)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private void disallowDuplicateSecretsManagerSecrets(List<SecretsManagerSecretResourceData> resources) {
        List<String> secretArns = resources.stream()
                .map(SecretsManagerSecretResourceData::arn)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findMultipleDestinations(secretArns);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> String.join("", "\"", string, "\""))
                    .collect(Collectors.joining(", "));

            log.error(String.join("", "Duplicate local secrets manager secrets defined [", duplicatesString, "].  Greengrass will not accept this configuration."));
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateDeviceSourcePaths(List<LocalDeviceResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(LocalDeviceResourceData::sourcePath)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findMultipleDestinations(sourcePaths);

        if (!duplicates.isPresent()) {
            return;
        }

        String duplicatesString = duplicates.get().stream()
                .map(string -> String.join("", "\"", string, "\""))
                .collect(Collectors.joining(", "));

        log.error(String.join("", "Duplicate local device resource source paths defined [", duplicatesString, "].  Greengrass will not accept this configuration."));
        throw new RuntimeException("Invalid resource configuration");
    }

    private void disallowDeviceSourcePathsNotInDev(List<LocalDeviceResourceData> resources) {
        List<String> invalidSourcePaths = resources.stream()
                .map(LocalDeviceResourceData::sourcePath)
                .filter(sourcePath -> !sourcePath.startsWith("/dev"))
                .collect(Collectors.toList());

        if (invalidSourcePaths.size() == 0) {
            return;
        }

        String invalidString = invalidSourcePaths.stream()
                .map(string -> String.join("", "\"", string, "\""))
                .collect(Collectors.joining(", "));

        log.error(String.join("", "Local device resource source paths were specified outside of /dev [", invalidString, "].  Greengrass will not accept this configuration."));
        throw new RuntimeException("Invalid resource configuration");
    }

    private void disallowDuplicateVolumeSourcePathsWithDifferentDestinationPaths(List<LocalVolumeResourceData> resources) {
        Map<String, Set<String>> map = new HashMap<>();

        // Find the destination paths for each source path
        resources.forEach(resource -> map.computeIfAbsent(resource.sourcePath(), x -> new HashSet<>()).add(resource.destinationPath()));

        // Isolate the source paths with more than one destination path
        Optional<List<Map.Entry<String, Set<String>>>> multipleDestinations = findMultipleDestinations(map);

        if (multipleDestinations.isPresent()) {
            String multipleDestinationsString = multipleDestinations.get().stream()
                    .map(entry -> String.join("", "Source [", entry.getKey(), "], destinations [", String.join(", ", entry.getValue()), "]"))
                    .collect(Collectors.joining(", "));

            log.error(String.join("", "Duplicate local volume resource destination paths defined [", multipleDestinationsString, "].  Greengrass will not accept this configuration."));

            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateSageMakerDestinationPaths(List<SageMakerMachineLearningModelResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(SageMakerMachineLearningModelResourceData::destinationPath)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findMultipleDestinations(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> String.join("", "\"", string, "\""))
                    .collect(Collectors.joining(", "));

            log.error(String.join("", "Duplicate local SageMaker resource destination paths defined [", duplicatesString, "].  Greengrass will not accept this configuration."));
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private void disallowDuplicateS3DestinationPaths(List<S3MachineLearningModelResourceData> resources) {
        List<String> sourcePaths = resources.stream()
                .map(S3MachineLearningModelResourceData::destinationPath)
                .collect(Collectors.toList());

        Optional<List<String>> duplicates = findMultipleDestinations(sourcePaths);

        if (duplicates.isPresent()) {
            String duplicatesString = duplicates.get().stream()
                    .map(string -> String.join("", "\"", string, "\""))
                    .collect(Collectors.joining(", "));

            log.error(String.join("", "Duplicate local S3 resource destination paths defined [", duplicatesString, "].  Greengrass will not accept this configuration."));
            throw new RuntimeException("Invalid resource configuration");
        }
    }

    private Optional<List<String>> findMultipleDestinations(List<String> inputList) {
        List<String> outputList = new ArrayList<>(inputList);
        Set<String> deduplicatedList = new HashSet<>(inputList);

        if (outputList.size() != deduplicatedList.size()) {
            deduplicatedList
                    .forEach(outputList::remove);

            return Optional.of(outputList);
        }

        return Optional.empty();
    }

    private Optional<List<Map.Entry<String, Set<String>>>> findMultipleDestinations(Map<String, Set<String>> inputMap) {
        List<Map.Entry<String, Set<String>>> duplicates = inputMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toList());

        if (duplicates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(duplicates);
    }
}
