package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ImmutableFunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.iot.data.ImmutableGreengrassGroupName;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import com.awslabs.lambda.data.ImmutableFunctionAlias;
import com.awslabs.lambda.data.ImmutableFunctionName;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.greengrass.model.EncodingType;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.Subscription;

import java.io.File;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.mock;

public class SubscriptionHelperTest {
    private BasicSubscriptionHelper basicSubscriptionHelper;
    private ImmutableFunctionConf fakeFunctionConf;

    @Before
    public void setup() {
        IoHelper ioHelper = mock(IoHelper.class);
        V2IotHelper v2IotHelper = mock(V2IotHelper.class);
        GGVariables ggVariables = mock(GGVariables.class);
        GGConstants ggConstants = mock(GGConstants.class);

        basicSubscriptionHelper = new BasicSubscriptionHelper();
        basicSubscriptionHelper.ioHelper = ioHelper;
        basicSubscriptionHelper.v2IotHelper = v2IotHelper;
        basicSubscriptionHelper.ggConstants = ggConstants;
        basicSubscriptionHelper.ggVariables = ggVariables;

        fakeFunctionConf = ImmutableFunctionConf.builder()
                .language(Language.EXECUTABLE)
                .encodingType(EncodingType.BINARY)
                .buildDirectory(new File(".").toPath())
                .groupName(ImmutableGreengrassGroupName.builder().groupName("test-group").build())
                .functionName(ImmutableFunctionName.builder().name("test-function").build())
                .handlerName("test-handler")
                .aliasName(ImmutableFunctionAlias.builder().alias("test-alias").build())
                .memorySizeInKb(1024)
                .isPinned(false)
                .timeoutInSeconds(10)
                .isAccessSysFs(false)
                .isGreengrassContainer(false)
                .uid(1000)
                .gid(1000)
                .rawConfig("")
                .build();
    }

    @Test
    public void testTopicCandidateMethod() {
        testTopicCandidateBothWays("a", "a", "a");

        testTopicCandidateBothWays("a", "b", Optional.empty());

        testTopicCandidateBothWays("a/b", "a/+", "a/b");

        testTopicCandidateBothWays("a/+", "a/+", "a/+");

        testTopicCandidateBothWays("a/more/complex/+/thing", "a/more/#", "a/more/complex/+/thing");
        testTopicCandidateBothWays("a/more/complex/+/thing", "a/more/complex/+/thing", "a/more/complex/+/thing");
        testTopicCandidateBothWays("a/more/complex/+/thing", "a/more/complex/y/thing", "a/more/complex/y/thing");

        testTopicCandidateBothWays("a/+/+/+/thing", "a/more/complex/+/thing", "a/more/complex/+/thing");

        testTopicCandidateBothWays("a/+/#", "a/more/complex/+/thing", "a/more/complex/+/thing");

        testTopicCandidateBothWays("/poorlyformedtopic", "/poorlyformedtopic", "/poorlyformedtopic");

        // NOTE: Topics with trailing slashes are not handled yet
        // testTopicCandidateBothWays("/poorlyformedtopic/", "/poorlyformedtopic/", "/poorlyformedtopic/");
    }

    private void testTopicCandidateBothWays(String input1, String input2, String expectedOutput) {
        testTopicCandidateBothWays(input1, input2, Optional.of(expectedOutput));
    }

    private void testTopicCandidateBothWays(String input1, String input2, Optional<String> expectedOutput) {
        MatcherAssert.assertThat(basicSubscriptionHelper.topicCandidate(input1, input2), is(expectedOutput));
        MatcherAssert.assertThat(basicSubscriptionHelper.topicCandidate(input2, input1), is(expectedOutput));
    }

    @Test
    public void simpleDirectFunctionToFunctionTopicMappingTest() {
        List<String> topics = Arrays.asList("a", "b", "c", "d", "e");
        Map<Function, FunctionConf> map = new HashMap<>();

        ImmutableFunctionConf abInput = ImmutableFunctionConf.builder().from(fakeFunctionConf)
                .inputTopics(topics)
                .outputTopics(new ArrayList<>())
                .build();
        String abInputArn = "abInputArn";
        Function abInputFunction = Function.builder()
                .functionArn(abInputArn)
                .build();

        map.put(abInputFunction, abInput);

        ImmutableFunctionConf abOutput = ImmutableFunctionConf.builder().from(fakeFunctionConf)
                .outputTopics(topics)
                .inputTopics(new ArrayList<>())
                .build();
        String abOutputArn = "abOutputArn";
        Function abOutputFunction = Function.builder()
                .functionArn(abOutputArn)
                .build();

        map.put(abOutputFunction, abOutput);

        List<GGDConf> ggdConfList = new ArrayList<>();

        List<Subscription> output = basicSubscriptionHelper.connectFunctionsAndDevices(map, ggdConfList);

        Assert.assertTrue(topics.stream().allMatch(topic -> oneMatches(output, abInputArn, abOutputArn, topic)));
    }

    private boolean oneMatches(List<Subscription> subscriptions, String expectedTarget, String expectedSource, String expectedSubject) {
        return subscriptions.stream()
                .anyMatch(subscription -> subscription.source().equals(expectedSource) &&
                        subscription.subject().equals(expectedSubject) &&
                        subscription.target().equals(expectedTarget));
    }
}
