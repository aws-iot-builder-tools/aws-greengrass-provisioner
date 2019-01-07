package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.SDK;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public interface ScriptingFunctionBuilder extends FunctionBuilder {
    void buildFunctionIfNecessary(FunctionConf functionConf);

    SDK getSdk();

    String getSdkDestinationPath();

    default void copySdk(Logger log, FunctionConf functionConf, ResourceHelper resourceHelper, IoHelper ioHelper) {
        String buildDirectory = functionConf.getBuildDirectory().toString();

        String sdkFullPath = getSdk().getFullSdkPath();

        String sdkInnerZipPath = getSdk().getInnerSdkZipPath();

        Try.of(() -> {
            // Try to get the SDK from inside the JAR
            Optional<InputStream> optionalInputStream = Optional.ofNullable(resourceHelper.getFileOrResourceAsStream(sdkInnerZipPath));

            if (!optionalInputStream.isPresent()) {
                // Couldn't find the SDK inside the JAR.  Try to get it on the local file system.
                if (!new File(sdkFullPath).exists()) {
                    // Couldn't find it on the local file system, give up
                    log.error("The SDK [" + getSdk().getFullSdkFilename() + "] is missing, please download it from the Greengrass console and put it in the [" + getSdk().getFOUNDATION() + "] directory");
                    System.exit(1);
                }

                // Get the inner SDK ZIP file from the full SDK
                optionalInputStream = ioHelper.extractTarGz(getSdk().getFullSdkPath(), getSdk().getInnerSdkZipFilename());
            }

            if (!optionalInputStream.isPresent()) {
                log.error("The SDK ZIP file [" + sdkInnerZipPath + "] is missing in the SDK.  This should never happen.  Please report this bug.");
                System.exit(1);
            }

            File destinationPath = new File(String.join("/", buildDirectory, getSdkDestinationPath()));

            ZipInputStream zis = new ZipInputStream(optionalInputStream.get());
            ZipEntry entry;

            // Create the base path if necessary
            new File(destinationPath.toString()).mkdirs();

            // Extract the entire SDK to where we are doing our build
            while ((entry = zis.getNextEntry()) != null) {
                String filename = entry.getName();
                filename = trimFilenameIfNecessary(filename);
                String path = String.join("/", destinationPath.toString(), filename);

                if (entry.isDirectory()) {
                    new File(path).mkdirs();
                } else {
                    File newFile = new File(path);
                    FileOutputStream fileOutputStream = new FileOutputStream(newFile);
                    int length;
                    byte[] buffer = new byte[8192];

                    while ((length = zis.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, length);
                    }

                    fileOutputStream.close();
                }
            }

            return null;
        });
    }

    default String trimFilenameIfNecessary(String filename) {
        // Assume no filename trimming is necessary
        return filename;
    }

    default void moveDeploymentPackage(FunctionConf functionConf, File tempFile) {
        Try.of(() -> Files.move(tempFile.toPath(), new File(getArchivePath(functionConf)).toPath(), StandardCopyOption.REPLACE_EXISTING));
    }

    default String getArchivePath(FunctionConf functionConf) {
        return String.join("/", functionConf.getBuildDirectory().toString(), functionConf.getFunctionName() + ".zip");
    }
}
