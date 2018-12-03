package com.awslabs.aws.greengrass.provisioner.interfaces.builders;

import com.awslabs.aws.greengrass.provisioner.data.SDK;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.amazonaws.util.ClassLoaderHelper.getResourceAsStream;

public interface ScriptingFunctionBuilder extends FunctionBuilder {
    void buildFunctionIfNecessary(FunctionConf functionConf);

    SDK getSdk();

    String getSdkDestinationPath();

    default void copySdk(Logger log, FunctionConf functionConf) {
        String buildDirectory = functionConf.getBuildDirectory().toString();

        String sdkFullPath = getSdk().getFullSdkPath();

        String sdkInnerZipPath = getSdk().getInnerSdkZipPath();

        try {
            // Try to get the SDK from inside the JAR
            InputStream inputStream = getResource(sdkInnerZipPath);

            if (inputStream == null) {
                // Couldn't find the SDK inside the JAR.  Try to get it on the local file system.
                if (!new File(sdkFullPath).exists()) {
                    // Couldn't find it on the local file system, give up
                    log.error("The SDK [" + getSdk().getFullSdkFilename() + "] is missing, please download it from the Greengrass console and put it in the [" + getSdk().getFOUNDATION() + "] directory");
                    System.exit(1);
                }

                // Get the inner SDK ZIP file from the full SDK
                inputStream = extract();
            }

            if (inputStream == null) {
                log.error("The SDK ZIP file [" + sdkInnerZipPath + "] is missing in the SDK.  This should never happen.  Please report this bug.");
                System.exit(1);
            }

            File destinationPath = new File(String.join("/", buildDirectory, getSdkDestinationPath()));

            ZipInputStream zis = new ZipInputStream(inputStream);
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
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    default InputStream extract() {
        try (final TarArchiveInputStream tarIn = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(new File(getSdk().getFullSdkPath()))))) {
            TarArchiveEntry tarEntry = tarIn.getNextTarEntry();

            while (tarEntry != null) {
                String currentFileName = tarEntry.getName();

                if (!currentFileName.endsWith(getSdk().getInnerSdkZipFilename())) {
                    tarEntry = tarIn.getNextTarEntry();
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                int length = 0;

                byte[] buffer = new byte[16384];

                while ((length = tarIn.read(buffer)) != -1) {
                    baos.write(buffer, 0, length);
                }

                return new ByteArrayInputStream(baos.toByteArray());
            }
        } catch (FileNotFoundException e) {
            throw new UnsupportedOperationException(e);
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }

        return null;
    }

    default InputStream getResource(String sourcePath) {
        File resource = new File(sourcePath);

        if (!resource.exists()) {
            if (!sourcePath.startsWith("/")) {
                // All resources inside a JAR must be absolute
                sourcePath = "/" + sourcePath;
            }

            return getResourceAsStream(sourcePath);
        }

        try {
            return new FileInputStream(resource);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    default String trimFilenameIfNecessary(String filename) {
        // Assume no filename trimming is necessary
        return filename;
    }

    default void moveDeploymentPackage(FunctionConf functionConf, File tempFile) {
        try {
            Files.move(tempFile.toPath(), new File(getArchivePath(functionConf)).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        }
    }

    default String getArchivePath(FunctionConf functionConf) {
        return String.join("/", functionConf.getBuildDirectory().toString(), functionConf.getFunctionName() + ".zip");
    }
}
