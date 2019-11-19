package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SshHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ThreadHelper;
import com.jcraft.jsch.Session;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class BasicSshHelper implements SshHelper {
    @Inject
    ThreadHelper threadHelper;
    @Inject
    IoHelper ioHelper;

    @Inject
    public BasicSshHelper() {
    }

    @NotNull
    @Override
    public Session getSshSession(String ipAddress, String user) {
        return getSshSession(ipAddress, user, SSH_CONNECTED_MESSAGE, SSH_TIMED_OUT_MESSAGE, SSH_CONNECTION_REFUSED_MESSAGE, SSH_ERROR_MESSAGE, 2, TimeUnit.MINUTES);
    }

    @NotNull
    @Override
    public Session getSshSession(String ipAddress, String user, String connectedMessage, String timedOutMessage, String connectionRefusedMessage, String errorMessage, int timeout, TimeUnit timeoutTimeUnit) {
        Optional<Session> optionalSession = threadHelper.timeLimitTask(
                ioHelper.getSshSessionTask(ipAddress,
                        user,
                        connectedMessage,
                        timedOutMessage,
                        connectionRefusedMessage,
                        errorMessage), timeout, timeoutTimeUnit);

        if (!optionalSession.isPresent()) {
            throw new RuntimeException("Failed to obtain the SSH session");
        }

        return optionalSession.get();
    }

    @Override
    public String[] getUserAndHost(String type, String input) {
        String[] strings = input.split("@");

        if (strings.length != 2) {
            throw new RuntimeException("Invalid " + type + " format. Specify the " + type + " as user@host (e.g. pi@192.168.1.5).");
        }

        return strings;
    }
}
