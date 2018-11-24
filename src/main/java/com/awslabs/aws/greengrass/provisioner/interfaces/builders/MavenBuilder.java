package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.FunctionConf;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MavenBuilder extends FunctionBuilder {
    String getArchivePath(FunctionConf functionConf);

    String getPomXmlPath(FunctionConf functionConf);

    boolean isMavenFunction(FunctionConf functionConf);

    void buildJavaFunctionIfNecessary(FunctionConf functionConf);

    void installDependencies();

    void runMaven(Optional<File> pomXmlPath, Optional<String> functionName, List<String> goals, Optional<Map<String, String>> properties);
}
