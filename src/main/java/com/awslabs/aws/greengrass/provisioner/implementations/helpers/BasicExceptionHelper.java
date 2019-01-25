package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;

public class BasicExceptionHelper implements ExceptionHelper {
    @Override
    public Void rethrowAsRuntimeException(Throwable throwable) {
        throw new RuntimeException(throwable);
    }
}
