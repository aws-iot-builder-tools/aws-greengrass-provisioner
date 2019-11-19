package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.jcraft.jsch.Session;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public interface SshHelper {
    String SSH_CONNECTED_MESSAGE = "Connected to host via SSH";
    String SSH_TIMED_OUT_MESSAGE = "SSH connection timed out, instance may still be starting up...";
    String SSH_CONNECTION_REFUSED_MESSAGE = "SSH connection refused, instance may still be starting up...";
    String SSH_ERROR_MESSAGE = "There was an SSH error [{}]";

    @NotNull Session getSshSession(String ipAddress, String user);

    @NotNull Session getSshSession(String ipAddress, String user, String connectedMessage, String timedOutMessage, String connectionRefusedMessage, String errorMessage, int timeout, TimeUnit timeoutTimeUnit);

    String[] getUserAndHost(String type, String input);
}
