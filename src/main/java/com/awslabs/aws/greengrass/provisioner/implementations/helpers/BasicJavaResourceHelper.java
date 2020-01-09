package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JavaResourceHelper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.*;
import java.util.Scanner;

import static com.google.common.io.Resources.getResource;

public class BasicJavaResourceHelper implements JavaResourceHelper {
    private final Logger log = LoggerFactory.getLogger(BasicGreengrassHelper.class);

    @Inject
    public BasicJavaResourceHelper() {
    }

    private String inputStreamToString(InputStream inputStream) {
        String string = new Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();

        return string;
    }

    @Override
    public InputStream getResourceAsStream(String resourcePath) {
        return Try.of(() -> getResource(resourcePath).openStream()).get();
    }

    @Override
    public InputStream getFileOrResourceAsStream(String sourcePath) {
        File resource = new File(sourcePath);

        if (!resource.exists()) {
            return getResourceAsStream(sourcePath);
        }

        return Try.of(() -> new FileInputStream(resource))
                .recover(FileNotFoundException.class, exception -> null)
                .get();
    }

    @Override
    public String resourceToString(String filename) {
        return inputStreamToString(getResourceAsStream(filename));
    }

    @Override
    public String resourceToTempFile(String filename) throws IOException {
        InputStream inputStream = getResourceAsStream(filename);

        if (inputStream == null) {
            throw new RuntimeException("Cannot get resource [" + filename + "] from JAR file.");
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
    }
}
