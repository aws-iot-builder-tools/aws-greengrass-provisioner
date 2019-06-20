package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import java.util.regex.Pattern;

public class MissingRuntimeWithTextErrorDiagnosticRule1 implements MissingRuntimeWithoutJsonDiagnosticRule {
    @Override
    public Pattern getPattern() {
        return Pattern.compile(".*unable to create worker process for (.*). cannot find executable (.*) under any of the provided paths.*");

    }
}
