package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;

import javax.inject.Inject;
import java.io.*;
import java.util.Scanner;

import static com.google.common.io.Resources.getResource;

public class BasicResourceHelper implements ResourceHelper {
    @Inject
    IoHelper ioHelper;

    @Inject
    public BasicResourceHelper() {
    }

    private String inputStreamToString(InputStream inputStream) {
        String string = new Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();

        return string;
    }

    @Override
    public InputStream getResourceAsStream(String resourcePath) {
        try {
            return getResource(resourcePath).openStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream getFileOrResourceAsStream(String sourcePath) {
        File resource = new File(sourcePath);

        if (!resource.exists()) {
            return getResourceAsStream(sourcePath);
        }

        try {
            return new FileInputStream(resource);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String resourceToString(String filename) {
        return inputStreamToString(getResourceAsStream(filename));
    }

    @Override
    public String resourceToTempFile(String filename) {
        try {
            InputStream inputStream = getResourceAsStream(filename);

            if (inputStream == null) {
                throw new Exception("Cannot get resource [" + filename + "] from JAR file.");
            }

            int readBytes;
            byte[] buffer = new byte[8192];

            File tempFile = File.createTempFile("temp", "jar");
            tempFile.deleteOnExit();
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);

            while ((readBytes = inputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, readBytes);
            }

            return tempFile.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
