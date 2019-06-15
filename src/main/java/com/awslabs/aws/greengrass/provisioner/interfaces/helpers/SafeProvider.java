package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import io.vavr.control.Try;
import software.amazon.awssdk.core.exception.SdkClientException;

import javax.inject.Provider;

public interface SafeProvider<T> extends Provider<T> {
    default T safeGet(SdkErrorHandler sdkErrorHandler) {
        return Try.of(this::unsafeGet)
                .recover(SdkClientException.class, throwable -> (T) sdkErrorHandler.handleSdkError(throwable))
                .get();
    }

    T unsafeGet();
}
