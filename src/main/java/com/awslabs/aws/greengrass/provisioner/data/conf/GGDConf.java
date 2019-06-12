package com.awslabs.aws.greengrass.provisioner.data.conf;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.List;

@Value.Immutable
public abstract class GGDConf {
    public abstract String getThingName();

    public abstract String getScriptName();

    public abstract Path getRootPath();

    public abstract List<String> getConnectedShadows();

    public abstract List<String> getFromCloudSubscriptions();

    public abstract List<String> getToCloudSubscriptions();

    public abstract List<String> getOutputTopics();

    public abstract List<String> getInputTopics();

    public abstract List<String> getFiles();

    public abstract List<String> getDependencies();
}
