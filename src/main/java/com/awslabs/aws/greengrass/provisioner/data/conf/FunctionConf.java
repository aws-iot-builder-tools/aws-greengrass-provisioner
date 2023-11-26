package com.awslabs.aws.greengrass.provisioner.data.conf;

import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
import com.awslabs.iot.data.GreengrassGroupName;
import com.awslabs.lambda.data.FunctionAlias;
import com.awslabs.lambda.data.FunctionName;
import com.awslabs.lambda.data.ImmutableFunctionName;
import org.immutables.value.Value;
import software.amazon.awssdk.services.greengrass.model.EncodingType;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
public abstract class FunctionConf {
    public abstract Optional<String> getExistingArn();

    public abstract String getRawConfig();

    public abstract Language getLanguage();

    public abstract Language getLambdaLanguage();

    public abstract EncodingType getEncodingType();

    public abstract Optional<Path> getBuildDirectory();

    public abstract GreengrassGroupName getGroupName();

    public abstract FunctionName getFunctionName();

    public abstract String getHandlerName();

    public abstract FunctionAlias getAliasName();

    public abstract int getMemorySizeInKb();

    public abstract boolean isPinned();

    public abstract int getTimeoutInSeconds();

    public abstract List<String> getFromCloudSubscriptions();

    public abstract List<String> getToCloudSubscriptions();

    public abstract List<String> getOutputTopics();

    public abstract List<String> getInputTopics();

    public abstract List<String> getConnectedShadows();

    public abstract List<LocalDeviceResource> getLocalDeviceResources();

    public abstract List<LocalVolumeResource> getLocalVolumeResources();

    public abstract List<LocalS3Resource> getLocalS3Resources();

    public abstract List<LocalSageMakerResource> getLocalSageMakerResources();

    public abstract List<LocalSecretsManagerResource> getLocalSecretsManagerResources();

    public abstract boolean isAccessSysFs();

    public abstract boolean isGreengrassContainer();

    public abstract Optional<Integer> getUid();

    public abstract Optional<Integer> getGid();

    public abstract Map<String, String> getEnvironmentVariables();

    public abstract Optional<File> getCfTemplate();

    public FunctionName getGroupFunctionName() {
        return ImmutableFunctionName.builder().name(String.join("-", getGroupName().getGroupName(), getFunctionName().getName())).build();
    }

    public abstract Optional<List<String>> getCoreRoleIamManagedPolicies();

    public abstract Optional<String> getCoreRoleIamPolicy();

    public abstract Optional<List<String>> getServiceRoleIamManagedPolicies();

    public abstract Optional<String> getServiceRoleIamPolicy();
}
