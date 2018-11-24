package com.awslabs.aws.greengrass.provisioner.docker.interfaces;

import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class GreengrassBuildImageResultCallback extends BuildImageResultCallback {
    @Getter
    private Optional<Throwable> buildThrowable = Optional.empty();
    @Getter
    private Optional<String> buildError = Optional.empty();
    private CountDownLatch finishLatch = new CountDownLatch(1);
    private String imageId;

    @Inject
    public GreengrassBuildImageResultCallback() {
    }

    public boolean error() {
        return (buildThrowable.isPresent() || buildError.isPresent());
    }

    public Optional<String> errorString() {
        if (!error()) {
            return Optional.empty();
        }

        if (buildThrowable.isPresent()) {
            return Optional.ofNullable(buildThrowable.get().getMessage());
        }

        return buildError;
    }

    @Override
    public void onStart(Closeable closeable) {
        log.debug("Docker build start");
    }

    @Override
    public void onNext(BuildResponseItem item) {
        if (item.isBuildSuccessIndicated()) {
            imageId = item.getImageId();
        }

        if (item.isErrorIndicated()) {
            buildError = Optional.ofNullable(item.getErrorDetail().getMessage());
            finishLatch.countDown();
        }

        if (item.getStream() == null) {
            return;
        }

        log.info("Docker build: " + item.getStream().trim());
    }

    @Override
    public String awaitImageId() {
        try {
            finishLatch.await();
            return imageId;
        } catch (InterruptedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        buildThrowable = Optional.of(throwable);
        finishLatch.countDown();
        log.error("Docker build error [" + throwable.getMessage() + "]");
    }

    @Override
    public void onComplete() {
        finishLatch.countDown();
        log.info("Docker build complete");
    }

    @Override
    public void close() {
        log.debug("Docker build closed");
    }
}
