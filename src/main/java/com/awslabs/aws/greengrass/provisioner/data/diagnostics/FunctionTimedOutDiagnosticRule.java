package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JsonHelper;
import io.vavr.Tuple3;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import javax.inject.Inject;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FunctionTimedOutDiagnosticRule implements DiagnosticRule {
    private final Pattern findJsonPattern = Pattern.compile(".*WorkItem reached max retry limit.*(\\{\"invocationId\":.*\\})");
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public FunctionTimedOutDiagnosticRule() {
    }

    @Override
    public Optional<List<String>> evaluate(Tuple3<LogGroup, LogStream, List<String>> input) {
        if (!isRuntimeLog(input)) {
            return Optional.empty();
        }

        return Optional.of(input._3.stream()
                .map(findJsonPattern::matcher)
                .filter(Matcher::matches)
                .map(this::buildErrorStrings)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
    }

    private Optional<List<String>> buildErrorStrings(Matcher matcher) {
        String json = matcher.group(1);
        Map error = jsonHelper.fromJson(Map.class, json.getBytes());

        if (!error.containsKey("funcArn")) {
            return Optional.empty();
        }

        String funcArn = (String) Optional.ofNullable(error.get("funcArn")).orElse("UNKNOWN");

        return Optional.of(Collections.singletonList(String.join("\n\t", "The function [" + funcArn + "] timed out and was killed.",
                "This could be because the function handler did not return before the timeoutInSeconds value for the function.",
                "All event-based invocations of a function must return before the timeoutInSeconds value.",
                "If the function has a long running task it must complete try starting the task in a thread and returning from the function handler.")));
    }
}
