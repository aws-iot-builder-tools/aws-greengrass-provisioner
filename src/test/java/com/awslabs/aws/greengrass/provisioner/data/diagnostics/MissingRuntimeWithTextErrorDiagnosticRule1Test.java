package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

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

public class MissingRuntimeWithTextErrorDiagnosticRule1Test {
    private final String runtime = "nodejs8.10";
    private final String functionArn = "arn:aws:lambda:us-east-1:zzz:function:yyy:11";
    private final String logLine = "unable to create worker process for " + functionArn + ". cannot find executable " + runtime + " under any of the provided paths [/usr/bin /usr/local/bin]";
    private MissingRuntimeWithTextErrorDiagnosticRule1 missingRuntimeForFunctionDiagnosticRule1;
    private Tuple3<LogGroup, LogStream, List<String>> log;

    @Before
    public void setup() {
        missingRuntimeForFunctionDiagnosticRule1 = new MissingRuntimeWithTextErrorDiagnosticRule1();
    }

    @Test
    public void shouldExtractFunctionArnAndRuntime() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.RUNTIME).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = missingRuntimeForFunctionDiagnosticRule1.evaluate(log);

        Assert.assertTrue(optionalResult.isPresent());
        List<String> result = optionalResult.get();
        Assert.assertThat(result.size(), is(1));
        String resultString = result.get(0);
        Assert.assertThat(resultString, containsString("[" + runtime + "]"));
        Assert.assertThat(resultString, containsString("[" + functionArn + "]"));
    }

    @Test
    public void shouldNotExtractFunctionArnAndRuntimeFromDifferentGroup() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GGIP_DETECTOR).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = missingRuntimeForFunctionDiagnosticRule1.evaluate(log);

        Assert.assertFalse(optionalResult.isPresent());
    }
}