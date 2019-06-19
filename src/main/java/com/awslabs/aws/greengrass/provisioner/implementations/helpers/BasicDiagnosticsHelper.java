package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.diagnostics.DiagnosticRule;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DiagnosticsHelper;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BasicDiagnosticsHelper implements DiagnosticsHelper {
    private final Logger log = LoggerFactory.getLogger(BasicDiagnosticsHelper.class);
    @Inject
    Set<DiagnosticRule> diagnosticRules;

    @Inject
    public BasicDiagnosticsHelper() {
    }

    @Override
    public void runDiagnostics(java.util.List<Tuple3<LogGroup, LogStream, String>> logs) {
        List<String> recommendations = logs.stream()
                .map(this::splitLog)
                .flatMap(this::evaluateRules)
                .distinct()
                .collect(Collectors.toList());

        recommendations.forEach(log::warn);
    }

    private Tuple3<LogGroup, LogStream, List<String>> splitLog(Tuple3<LogGroup, LogStream, String> log) {
        return Tuple.of(log._1, log._2, Arrays.asList(log._3.split("\n")));
    }

    private Stream<String> evaluateRules(Tuple3<LogGroup, LogStream, List<String>> log) {
        return diagnosticRules.stream()
                .map(diagnosticRule -> diagnosticRule.evaluate(log))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(Collection::stream);
    }

    @Override
    public String trimLogGroupName(LogGroup logGroup) {
        return logGroup.logGroupName().replaceAll("^.*/([^/].*)$", "$1");
    }
}

