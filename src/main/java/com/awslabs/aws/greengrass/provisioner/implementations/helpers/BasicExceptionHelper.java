package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.ExceptionHelper;

import javax.inject.Inject;

public class BasicExceptionHelper implements ExceptionHelper {
    @Inject
    public BasicExceptionHelper() {
    }

    @Override
    public Void rethrowAsRuntimeException(Throwable throwable) {
        throw new RuntimeException(throwable);
    }
}
