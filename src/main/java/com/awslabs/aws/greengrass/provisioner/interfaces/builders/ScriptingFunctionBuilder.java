package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.SDK;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;

public interface ScriptingFunctionBuilder extends FunctionBuilder {
    void buildFunctionIfNecessary(FunctionConf functionConf);

    Optional<SDK> getSdk();

    String getSdkDestinationPath();

    default void copySdk(Logger log, FunctionConf functionConf, ResourceHelper resourceHelper, IoHelper ioHelper) {
        if (!getSdk().isPresent()) {
            // SDK doesn't require manual installation, skip this
            return;
        }

        SDK sdk = getSdk().get();

        String buildDirectory = functionConf.getBuildDirectory().get().toString();

        String sdkFullPath = sdk.getFullSdkPath();

        String sdkInnerZipPath = sdk.getInnerSdkZipPath();

        Try.of(() -> extractSdkFromJar(log, sdk, resourceHelper, ioHelper, buildDirectory, sdkFullPath, sdkInnerZipPath))
                .get();
    }

    default Void extractSdkFromJar(Logger log, SDK sdk, ResourceHelper resourceHelper, IoHelper ioHelper, String buildDirectory, String sdkFullPath, String sdkInnerZipPath) {
        // Try to get the SDK from inside the JAR
        Optional<InputStream> optionalInputStream = Optional.ofNullable(resourceHelper.getFileOrResourceAsStream(sdkInnerZipPath));

        if (!optionalInputStream.isPresent()) {
            // Couldn't find the SDK inside the JAR.  Try to get it on the local file system.
            if (!new File(sdkFullPath).exists()) {
                // Couldn't find it on the local file system, give up
                log.error("The SDK [" + sdk.getFullSdkFilename() + "] is missing, please download it from the Greengrass console and put it in the [" + sdk.getFOUNDATION() + "] directory");
                System.exit(1);
            }

            // Get the inner SDK ZIP file from the full SDK
            optionalInputStream = ioHelper.extractTarGz(sdk.getFullSdkPath(), sdk.getInnerSdkZipFilename());
        }

        if (!optionalInputStream.isPresent()) {
            log.error("The SDK ZIP file [" + sdkInnerZipPath + "] is missing in the SDK.  This should never happen.  Please report this bug.");
            System.exit(1);
        }

        File destinationPath = new File(String.join("/", buildDirectory, getSdkDestinationPath()));

        Optional<InputStream> finalOptionalInputStream = optionalInputStream;

        return Try.of(() -> ioHelper.extractZip(finalOptionalInputStream.get(), destinationPath.toPath(), getFilenameTrimmer())).get();
    }

    default Function<String, String> getFilenameTrimmer() {
        // Assume no filename trimming is necessary
        return string -> string;
    }

    default void moveDeploymentPackage(FunctionConf functionConf, File tempFile) {
        Try.of(() -> Files.move(tempFile.toPath(), new File(getArchivePath(functionConf)).toPath(), StandardCopyOption.REPLACE_EXISTING))
                .get();
    }

    default String getArchivePath(FunctionConf functionConf) {
        return String.join("/", functionConf.getBuildDirectory().get().toString(), functionConf.getFunctionName() + ".zip");
    }

    boolean hasDependencies(Path buildDirectory);
}
