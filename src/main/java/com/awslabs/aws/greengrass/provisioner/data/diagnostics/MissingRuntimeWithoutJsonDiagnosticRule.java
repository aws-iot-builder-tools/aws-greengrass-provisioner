package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import io.vavr.Tuple3;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface MissingRuntimeWithoutJsonDiagnosticRule extends DiagnosticRule {
    default Optional<List<String>> evaluate(Tuple3<LogGroup, LogStream, List<String>> input) {
        if (!isRuntimeLog(input)) {
            return Optional.empty();
        }

        Pattern pattern = getPattern();

        return Optional.of(input._3.stream()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .map(this::buildErrorString)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList()));
    }

    @NotNull
    default Optional<String> buildErrorString(Matcher matcher) {
        String function = matcher.group(1);
        String runtime = matcher.group(2);

        return Optional.of(String.join("\n\t",
                String.join("", "The runtime [", runtime, "] appears to be missing."),
                String.join("", "This will prevent [", function, "] from running.")));
    }

    Pattern getPattern();
}
