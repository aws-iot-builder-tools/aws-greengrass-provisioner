package com.awslabs.aws.greengrass.provisioner.interfaces;

public interface ExceptionHelper {
    Void rethrowAsRuntimeException(Throwable throwable);
}
