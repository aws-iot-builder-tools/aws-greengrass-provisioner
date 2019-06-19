package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import io.vavr.Tuple3;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoConnectivityInformationDiagnosticRule implements DiagnosticRule {
    private final Pattern pattern = Pattern.compile(".*We do not have connectivity information for this GGC.*");

    @Inject
    public NoConnectivityInformationDiagnosticRule() {
    }

    @Override
    public Optional<List<String>> evaluate(Tuple3<LogGroup, LogStream, List<String>> input) {
        if (!isGgDeviceCertificateManager(input)) {
            return Optional.empty();
        }

        Optional<Matcher> optionalMatcher = input._3.stream()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .findFirst();

        if (!optionalMatcher.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(Collections.singletonList(String.join("\n\t", "This Greengrass core has not sent its connectivity information to the Greengrass service.",
                "This may be because it has too many IP addresses.")));
    }
}
