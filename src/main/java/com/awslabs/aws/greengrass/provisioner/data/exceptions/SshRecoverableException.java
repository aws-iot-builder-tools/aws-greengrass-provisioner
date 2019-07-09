package com.awslabs.aws.greengrass.provisioner.data.exceptions;

public class SshRecoverableException extends RuntimeException {
    public SshRecoverableException(String message) {
        super(message);
    }
}
