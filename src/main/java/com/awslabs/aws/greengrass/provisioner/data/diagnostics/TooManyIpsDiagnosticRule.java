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

public class TooManyIpsDiagnosticRule implements DiagnosticRule {
    private final Pattern pattern = Pattern.compile(".*Too many items in the Connectivity Information list.*");

    @Inject
    public TooManyIpsDiagnosticRule() {
    }

    @Override
    public Optional<List<String>> evaluate(Tuple3<LogGroup, LogStream, List<String>> input) {
        if (!isGgIpDetectorLog(input)) {
            return Optional.empty();
        }

        Optional<Matcher> optionalMatcher = input._3.stream()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .findFirst();

        if (!optionalMatcher.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(Collections.singletonList(String.join("\n\t", "This Greengrass core has too many IP addresses for the default IP address detector.",
                "Either set the connectivity information manually or run a custom IP detector.",
                "Until this issue is corrected this Greengrass core will not have a server certificate and will not be discoverable.")));
    }
}
