package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import io.vavr.Tuple3;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import java.util.List;

public interface DiagnosticsHelper {
    void runDiagnostics(List<Tuple3<LogGroup, LogStream, String>> logs);

    String trimLogGroupName(LogGroup logGroup);
}
