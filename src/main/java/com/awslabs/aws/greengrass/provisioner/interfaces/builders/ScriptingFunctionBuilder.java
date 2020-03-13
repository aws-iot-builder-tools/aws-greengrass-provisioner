package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import io.vavr.control.Try;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public interface ScriptingFunctionBuilder extends FunctionBuilder {
    void buildFunctionIfNecessary(FunctionConf functionConf);

    default void moveDeploymentPackage(FunctionConf functionConf, File tempFile) {
        Try.of(() -> Files.move(tempFile.toPath(), new File(getArchivePath(functionConf)).toPath(), StandardCopyOption.REPLACE_EXISTING))
                .get();
    }

    default String getArchivePath(FunctionConf functionConf) {
        return String.join("/", functionConf.getBuildDirectory().get().toString(),
                String.join(".", functionConf.getFunctionName().getName(), "zip"));
    }

    boolean hasDependencies(Path buildDirectory);
}
