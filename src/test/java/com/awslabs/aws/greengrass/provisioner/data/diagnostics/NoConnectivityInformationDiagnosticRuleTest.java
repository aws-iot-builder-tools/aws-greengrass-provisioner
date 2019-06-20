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

import static org.hamcrest.CoreMatchers.is;

public class NoConnectivityInformationDiagnosticRuleTest {
    private final String logLine = "Cert manager error: Failed to get connectivity info. CIS responded with status code: 404, and body: {\"Message\":\"We do not have connectivity information for this GGC.\"}";
    private NoConnectivityInformationDiagnosticRule noConnectivityInformationDiagnosticRule;
    private Tuple3<LogGroup, LogStream, List<String>> log;

    @Before
    public void setup() {
        noConnectivityInformationDiagnosticRule = new NoConnectivityInformationDiagnosticRule();
    }

    @Test
    public void shouldReportConnectivityInfoFailure() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GG_DEVICE_CERTIFICATE_MANAGER).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = noConnectivityInformationDiagnosticRule.evaluate(log);

        Assert.assertTrue(optionalResult.isPresent());
        List<String> result = optionalResult.get();
        Assert.assertThat(result.size(), is(1));
    }

    @Test
    public void shouldNotReportConnectivityInfoFailureFromDifferentLogGroup() {
        LogGroup logGroup = LogGroup.builder().logGroupName(DiagnosticRule.GGIP_DETECTOR).build();
        LogStream logStream = LogStream.builder().build();
        log = Tuple.of(logGroup, logStream, Collections.singletonList(logLine));

        Optional<List<String>> optionalResult = noConnectivityInformationDiagnosticRule.evaluate(log);

        Assert.assertFalse(optionalResult.isPresent());
    }
}