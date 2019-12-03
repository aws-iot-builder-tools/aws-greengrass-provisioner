package com.awslabs.aws.greengrass.provisioner.data.conf;

import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.resources.*;
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

    public abstract EncodingType getEncodingType();

    public abstract Optional<Path> getBuildDirectory();

    public abstract String getGroupName();

    public abstract String getFunctionName();

    public abstract String getHandlerName();

    public abstract String getAliasName();

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

    public abstract int getUid();

    public abstract int getGid();

    public abstract Map<String, String> getEnvironmentVariables();

    public abstract Optional<File> getCfTemplate();

    public String getGroupFunctionName() {
        return String.join("-", getGroupName(), getFunctionName());
    }
}
