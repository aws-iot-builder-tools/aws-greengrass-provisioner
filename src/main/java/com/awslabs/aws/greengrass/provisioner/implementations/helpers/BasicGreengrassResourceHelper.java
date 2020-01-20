package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GreengrassResourceHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.*;

import javax.inject.Inject;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.io.Resources.getResource;

public class BasicGreengrassResourceHelper implements GreengrassResourceHelper {
    private final Logger log = LoggerFactory.getLogger(BasicGreengrassHelper.class);

    @Inject
    public BasicGreengrassResourceHelper() {
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
