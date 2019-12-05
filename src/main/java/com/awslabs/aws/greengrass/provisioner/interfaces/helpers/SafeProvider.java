package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicSdkErrorHandler;
import io.vavr.control.Try;
import software.amazon.awssdk.core.exception.SdkClientException;

import javax.inject.Provider;
import java.util.concurrent.Callable;

public class SafeProvider<T> implements Provider<T> {
    private final Callable<T> callable;

    public SafeProvider(Callable<T> callable) {
        this.callable = callable;
    }

    public T get() {
        SdkErrorHandler sdkErrorHandler = new BasicSdkErrorHandler();

        return Try.of(callable::call)
                .recover(SdkClientException.class, throwable -> (T) sdkErrorHandler.handleSdkError(throwable))
                .get();
    }
}
