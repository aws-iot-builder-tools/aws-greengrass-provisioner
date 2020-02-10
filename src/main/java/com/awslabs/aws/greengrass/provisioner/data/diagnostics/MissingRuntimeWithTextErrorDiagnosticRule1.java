package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import javax.inject.Inject;
import java.util.regex.Pattern;

public class MissingRuntimeWithTextErrorDiagnosticRule1 implements MissingRuntimeWithoutJsonDiagnosticRule {
    @Inject
    public MissingRuntimeWithTextErrorDiagnosticRule1() {
    }

    @Override
    public Pattern getPattern() {
        return Pattern.compile(".*unable to create worker process for (.*). cannot find executable (.*) under any of the provided paths.*");

    }
}
