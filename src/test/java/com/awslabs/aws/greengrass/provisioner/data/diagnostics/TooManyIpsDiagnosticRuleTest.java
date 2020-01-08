package com.awslabs.aws.greengrass.provisioner.data.diagnostics;

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

import static org.hamcrest.CoreMatchers.is;

public class TooManyIpsDiagnosticRuleTest {
    private final String logLine = "The server says: {\"Message\":\"Too many items in the Connectivity Information list. You can store a maximum of 10 endpoints.\"}";

    private TooManyIpsDiagnosticRule tooManyIpsDiagnosticRule;
    private Tuple3<LogGroup, LogStream, List<String>> log;

    @Before
    public void setup() {
        tooManyIpsDiagnosticRule = new TooManyIpsDiagnosticRule();
    }

    @Test
    public void shouldReportTooManyItems() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GGIP_DETECTOR).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = tooManyIpsDiagnosticRule.evaluate(log);

        Assert.assertTrue(optionalResult.isPresent());
        List<String> result = optionalResult.get();
        MatcherAssert.assertThat(result.size(), is(1));
    }

    @Test
    public void shouldNotReportTooManyItemsFromDifferentLogGroup() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GG_DEVICE_CERTIFICATE_MANAGER).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = tooManyIpsDiagnosticRule.evaluate(log);

        Assert.assertFalse(optionalResult.isPresent());
    }
}