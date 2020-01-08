package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicJsonHelper;
import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

public class MissingRuntimeWithJsonErrorDiagnosticRule1Test {
    private static final String runtime = "nodejs8.10";
    private static final String logLine = "runtime execution error: unable to start lambda container.       {\"errorString\": \"failed to run container sandbox: container_linux.go:344: starting container process caused \\\"exec: \\\\\\\"" + runtime + "\\\\\\\": executable file not found in $PATH\\\"\"}";
    private MissingRuntimeWithJsonErrorDiagnosticRule1 missingRuntimeDiagnosticRule1;
    private Tuple3<LogGroup, LogStream, List<String>> log;

    @Before
    public void setup() {
        missingRuntimeDiagnosticRule1 = new MissingRuntimeWithJsonErrorDiagnosticRule1();
        missingRuntimeDiagnosticRule1.jsonHelper = new BasicJsonHelper();
    }

    @Test
    public void shouldReportNode8_10Missing() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.RUNTIME).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = missingRuntimeDiagnosticRule1.evaluate(log);

        Assert.assertTrue(optionalResult.isPresent());
        List<String> result = optionalResult.get();
        MatcherAssert.assertThat(result.size(), is(1));
        String resultString = result.get(0);
        MatcherAssert.assertThat(resultString, containsString("[" + runtime + "]"));
    }

    @Test
    public void shouldNotReportNode8_10MissingFromDifferentLogGroup() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GGIP_DETECTOR).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = missingRuntimeDiagnosticRule1.evaluate(log);

        Assert.assertFalse(optionalResult.isPresent());
    }
}