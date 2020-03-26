package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.lambda.data.FunctionName;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public interface GradleBuilder extends FunctionBuilder {
    String getArchivePath(FunctionConf functionConf);

    String getGradleBuildPath(FunctionConf functionConf);

    boolean isGradleFunction(FunctionConf functionConf);

    boolean isGradleFunction(Path path);

    void buildJavaFunctionIfNecessary(FunctionConf functionConf);

    void runGradle(Optional<File> gradleBuildPath, Optional<FunctionName> functionName);
}
