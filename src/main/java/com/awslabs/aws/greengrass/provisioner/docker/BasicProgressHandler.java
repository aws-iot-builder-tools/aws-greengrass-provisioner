package com.awslabs.aws.greengrass.provisioner.docker;

import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ProgressMessage;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class BasicProgressHandler implements ProgressHandler {
    @Inject
    public BasicProgressHandler() {
    }

    @Override
    public void progress(ProgressMessage message) throws DockerException {
        if (message.error() != null) {
            log.error("Docker build error [" + message.error() + "]");
            return;
        }

        if (message.status() != null) {
            log.info("Status: " + message.status());
        }

        if (message.progress() != null) {
            log.info("Progress: " + message.progress());
        }

        if (message.stream() != null) {
            log.info("Stream: " + message.stream());
        }
    }
}
