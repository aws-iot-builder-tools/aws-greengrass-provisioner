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

public class FunctionTimedOutDiagnosticRuleTest {
    private static final String funcArn = "arn:aws:lambda:us-east-1:yyy:function:zzz:47";
    private static final String logLine = "WorkItem reached max retry limit.     {\"invocationId\": \"6a92c46c-7ce8-4c00-70e4-e70d33628862\", \"funcArn\": \"" + funcArn + "\", \"workerId\": \"32b93434-787f-4bc1-4453-a1bc2538b711\"}";
    private FunctionTimedOutDiagnosticRule functionTimedOutDiagnosticRule;
    private Tuple3<LogGroup, LogStream, List<String>> log;

    @Before
    public void setup() {
        functionTimedOutDiagnosticRule = new FunctionTimedOutDiagnosticRule();
        functionTimedOutDiagnosticRule.jsonHelper = new BasicJsonHelper();
    }

    @Test
    public void shouldReportFunctionTimedOut() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.RUNTIME).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = functionTimedOutDiagnosticRule.evaluate(log);

        Assert.assertTrue(optionalResult.isPresent());
        List<String> result = optionalResult.get();
        Assert.assertThat(result.size(), is(1));
        String resultString = result.get(0);
        Assert.assertThat(resultString, containsString("[" + funcArn + "]"));
    }

    @Test
    public void shouldNotReportFunctionTimedOutFromDifferentLogGroup() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GGIP_DETECTOR).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = functionTimedOutDiagnosticRule.evaluate(log);

        Assert.assertFalse(optionalResult.isPresent());
    }
}