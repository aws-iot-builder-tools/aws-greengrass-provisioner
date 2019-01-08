package com.awslabs.aws.greengrass.provisioner.docker;

import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ProgressMessage;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class BasicProgressHandler implements ProgressHandler {
    String lastMessage = null;

    @Inject
    public BasicProgressHandler() {
    }

    @Override
    public void progress(ProgressMessage message) throws DockerException {
        if (message.error() != null) {
            log.error("Docker build error [" + message.error() + "]");
            return;
        }

        if ((message.status() != null) && (!message.status().equals(lastMessage))) {
            log.info("Status: " + message.status());
            lastMessage = message.status();
        }

        if ((message.progress() != null) && (!message.progress().equals(lastMessage))) {
            log.info("Progress: " + message.progress());
            lastMessage = message.progress();
        }

        if ((message.stream() != null) && (!message.stream().equals(lastMessage))) {
            log.info("Stream: " + message.stream());
            lastMessage = message.stream();
        }
    }
}
