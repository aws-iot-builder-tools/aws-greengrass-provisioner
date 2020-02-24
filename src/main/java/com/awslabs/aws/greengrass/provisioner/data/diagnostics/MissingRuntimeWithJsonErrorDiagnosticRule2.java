package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import com.awslabs.aws.iot.resultsiterator.helpers.interfaces.JsonHelper;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class MissingRuntimeWithJsonErrorDiagnosticRule2 implements MissingRuntimeWithJsonErrorDiagnosticRule {
    private final Pattern findJsonPattern = Pattern.compile(".*Failed to start worker.*(\\{\"workerId\":.*\\})");
    private final Pattern findRuntimePattern = Pattern.compile(".*exec: (.*):.*");

    @Inject
    JsonHelper jsonHelper;

    @Inject
    public MissingRuntimeWithJsonErrorDiagnosticRule2() {
    }

    @Override
    public Pattern getFindJsonPattern() {
        return findJsonPattern;
    }

    @Override
    public JsonHelper getJsonHelper() {
        return jsonHelper;
    }

    @Override
    public Optional<List<String>> buildErrorString(Map error, String runtime) {
        String functionArn = (String) Optional.ofNullable(error.get("functionArn")).orElse("UNKNOWN");

        return Optional.of(Collections.singletonList(String.join("\n\t", "The runtime [" + runtime + "] appears to be missing.",
                "This will prevent [" + functionArn + "] from running.")));
    }
}
