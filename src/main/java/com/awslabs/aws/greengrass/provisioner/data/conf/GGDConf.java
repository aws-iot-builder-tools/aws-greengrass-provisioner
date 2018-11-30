package com.awslabs.aws.greengrass.provisioner.data.conf;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
@Builder
public class GGDConf {
    private String thingName;
    private String scriptName;
    private Path rootPath;

    private List<String> connectedShadows;

    private List<String> fromCloudSubscriptions;
    private List<String> toCloudSubscriptions;
    private List<String> outputTopics;
    private List<String> inputTopics;

    private List<String> files;

    private List<String> dependencies;
}
