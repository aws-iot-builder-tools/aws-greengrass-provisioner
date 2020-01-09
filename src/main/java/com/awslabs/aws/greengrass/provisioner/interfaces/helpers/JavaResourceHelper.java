package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import java.io.IOException;
import java.io.InputStream;

public interface JavaResourceHelper {
    InputStream getResourceAsStream(String resourcePath);

    InputStream getFileOrResourceAsStream(String sourcePath);

    String resourceToString(String filename);

    String resourceToTempFile(String filename) throws IOException;
}
