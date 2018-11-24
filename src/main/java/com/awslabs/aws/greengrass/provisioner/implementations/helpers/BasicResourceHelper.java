package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Scanner;

import static com.amazonaws.util.ClassLoaderHelper.getResourceAsStream;

public class BasicResourceHelper implements ResourceHelper {
    @Inject
    public BasicResourceHelper() {
    }

    private String inputStreamToString(InputStream inputStream) {
        String string = new Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();

        return string;
    }

    @Override
    public String resourceToString(String filename) {
        return inputStreamToString(getResourceAsStream(filename));
    }

    @Override
    public String resourceToTempFile(String filename) {
        try {
            InputStream inputStream = getResourceAsStream(filename);//note that each / is a directory down in the "jar tree" been the jar the root of the tree
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
            throw new UnsupportedOperationException(e);
        }
    }
}
