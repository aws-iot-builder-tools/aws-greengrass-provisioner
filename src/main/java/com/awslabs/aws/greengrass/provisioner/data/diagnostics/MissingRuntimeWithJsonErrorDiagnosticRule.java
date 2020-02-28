package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import com.awslabs.general.helpers.interfaces.JsonHelper;
import io.vavr.Tuple3;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface MissingRuntimeWithJsonErrorDiagnosticRule extends DiagnosticRule {
    String QUOTING_STRING = "\\\\\"";

    Pattern getFindJsonPattern();

    default Pattern getFindRuntimePattern() {
        return Pattern.compile(".*exec: (.*):.*");
    }

    JsonHelper getJsonHelper();

    default Optional<List<String>> evaluate(Tuple3<LogGroup, LogStream, List<String>> input) {
        if (!isRuntimeLog(input)) {
            return Optional.empty();
        }

        return Optional.of(input._3.stream()
                .map(getFindJsonPattern()::matcher)
                .filter(Matcher::matches)
                .map(this::buildErrorStrings)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
    }

    default Optional<List<String>> buildErrorStrings(Matcher matcher) {
        String json = matcher.group(1);
        Map error = getJsonHelper().fromJson(Map.class, json.getBytes());

        Object errorStringObject = error.get("errorString");

        if (!errorStringObject.getClass().isAssignableFrom(String.class)) {
            return Optional.empty();
        }

        String errorString = (String) errorStringObject;

        Matcher runtimeMatcher = getFindRuntimePattern().matcher(errorString);

        if (!runtimeMatcher.matches()) {
            return Optional.empty();
        }

        String runtime = runtimeMatcher.group(1);
        runtime = runtime.replaceAll(QUOTING_STRING, "");

        return buildErrorString(error, runtime);
    }

    Optional<List<String>> buildErrorString(Map error, String runtime);
}
