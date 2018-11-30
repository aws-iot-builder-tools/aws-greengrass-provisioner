package com.awslabs.aws.greengrass.provisioner.data.conf;

import com.amazonaws.services.greengrass.model.EncodingType;
import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalDeviceResource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalS3Resource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalSageMakerResource;
import com.awslabs.aws.greengrass.provisioner.data.resources.LocalVolumeResource;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class FunctionConf {
    private Language language;
    private EncodingType encodingType;
    private Path buildDirectory;
    private String groupName;
    private String functionName;
    private String handlerName;
    private String aliasName;
    private int memorySizeInKb;
    private boolean pinned;
    private int timeoutInSeconds;

    private List<String> fromCloudSubscriptions;
    private List<String> toCloudSubscriptions;
    private List<String> outputTopics;
    private List<String> inputTopics;

    private List<String> connectedShadows;

    @Singular
    private List<LocalDeviceResource> localDeviceResources;

    @Singular
    private List<LocalVolumeResource> localVolumeResources;

    @Singular
    private List<LocalS3Resource> localS3Resources;

    @Singular
    private List<LocalSageMakerResource> localSageMakerResources;

    private List<String> dependencies;

    private boolean accessSysFs;

    @Singular
    private Map<String, String> environmentVariables;

    private File cfTemplate;
}
