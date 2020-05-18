package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import com.awslabs.general.helpers.interfaces.JsonHelper;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class MissingRuntimeWithJsonErrorDiagnosticRule1 implements MissingRuntimeWithJsonErrorDiagnosticRule {
    private final Pattern findJsonPattern = Pattern.compile(".*runtime execution error: unable to start lambda container.*(\\{\"errorString\":.*\\})");

    @Inject
    JsonHelper jsonHelper;

    @Inject
    public MissingRuntimeWithJsonErrorDiagnosticRule1() {
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
        return Optional.of(Collections.singletonList(String.join("\n\t",
                String.join("", "The runtime [", runtime, "] appears to be missing."),
                "This will prevent some functions from running.")));
    }
}
