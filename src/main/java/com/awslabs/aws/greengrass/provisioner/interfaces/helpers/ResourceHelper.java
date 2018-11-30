package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import java.io.InputStream;

public interface ResourceHelper {
    InputStream getResourceAsStream(String resourcePath);

    InputStream getFileOrResourceAsStream(String sourcePath);

    String resourceToString(String filename);

    String resourceToTempFile(String filename);
}
