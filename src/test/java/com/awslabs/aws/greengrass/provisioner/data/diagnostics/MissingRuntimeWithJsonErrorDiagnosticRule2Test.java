package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicJsonHelper;
import io.vavr.Tuple;
import io.vavr.Tuple3;
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

public class MissingRuntimeWithJsonErrorDiagnosticRule2Test {
    private final String runtime = "nodejs8.10";
    private final String functionArn = "arn:aws:lambda:us-east-1:zzz:function:yyy:11";
    private final String logLine = "Failed to start worker.       {\"workerId\": \"9cd6a690-7f33-4cd0-5dc5-61198aed5617\", \"functionArn\": \"" + functionArn + "\", \"errorString\": \"process start failed: failed to run container sandbox: container_linux.go:344: starting container process caused \\\"exec: \\\\\\\"" + runtime + "\\\\\\\": executable file not found in $PATH\\\"\"}";
    private MissingRuntimeWithJsonErrorDiagnosticRule2 missingRuntimeDiagnosticRule2;
    private Tuple3<LogGroup, LogStream, List<String>> log;

    @Before
    public void setup() {
        missingRuntimeDiagnosticRule2 = new MissingRuntimeWithJsonErrorDiagnosticRule2();
        missingRuntimeDiagnosticRule2.jsonHelper = new BasicJsonHelper();
    }

    @Test
    public void shouldExtractFunctionArnAndRuntime() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.RUNTIME).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = missingRuntimeDiagnosticRule2.evaluate(log);

        Assert.assertTrue(optionalResult.isPresent());
        List<String> result = optionalResult.get();
        Assert.assertThat(result.size(), is(1));
        String resultString = result.get(0);
        Assert.assertThat(resultString, containsString("[" + runtime + "]"));
        Assert.assertThat(resultString, containsString("[" + functionArn + "]"));
    }

    @Test
    public void shouldNotExtractFunctionArnAndRuntimeFromDifferentLogGroup() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GGIP_DETECTOR).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = missingRuntimeDiagnosticRule2.evaluate(log);

        Assert.assertFalse(optionalResult.isPresent());
    }
}