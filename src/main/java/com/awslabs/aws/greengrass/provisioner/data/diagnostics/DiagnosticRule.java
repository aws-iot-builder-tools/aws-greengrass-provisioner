package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import io.vavr.Tuple3;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import java.util.List;
import java.util.Optional;

public interface DiagnosticRule {
    String GGIP_DETECTOR = "GGIPDetector";
    String GG_DEVICE_CERTIFICATE_MANAGER = "GGDeviceCertificateManager";
    String RUNTIME = "runtime";

    Optional<List<String>> evaluate(Tuple3<LogGroup, LogStream, List<String>> input);

    default boolean isGgIpDetectorLog(Tuple3<LogGroup, LogStream, List<String>> input) {
        return input._1.logGroupName().endsWith(GGIP_DETECTOR);
    }

    default boolean isGgDeviceCertificateManager(Tuple3<LogGroup, LogStream, List<String>> input) {
        return input._1.logGroupName().endsWith(GG_DEVICE_CERTIFICATE_MANAGER);
    }

    default boolean isRuntimeLog(Tuple3<LogGroup, LogStream, List<String>> input) {
        return input._1.logGroupName().endsWith(RUNTIME);
    }
}
