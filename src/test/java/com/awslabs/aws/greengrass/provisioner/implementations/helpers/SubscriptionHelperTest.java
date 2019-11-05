package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Language;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.ImmutableFunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.greengrass.model.EncodingType;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.Subscription;

import java.io.File;
import java.util.*;

import static org.mockito.Mockito.mock;

public class SubscriptionHelperTest {
    private BasicSubscriptionHelper basicSubscriptionHelper;
    private ImmutableFunctionConf fakeFunctionConf;

    @Before
    public void setup() {
        IoHelper ioHelper = mock(IoHelper.class);
        IotHelper iotHelper = mock(IotHelper.class);
        GGVariables ggVariables = mock(GGVariables.class);
        GGConstants ggConstants = mock(GGConstants.class);

        basicSubscriptionHelper = new BasicSubscriptionHelper();
        basicSubscriptionHelper.ioHelper = ioHelper;
        basicSubscriptionHelper.iotHelper = iotHelper;
        basicSubscriptionHelper.ggConstants = ggConstants;
        basicSubscriptionHelper.ggVariables = ggVariables;

        fakeFunctionConf = ImmutableFunctionConf.builder()
                .language(Language.EXECUTABLE)
                .encodingType(EncodingType.BINARY)
                .buildDirectory(new File(".").toPath())
                .groupName("test-group")
                .functionName("test-function")
                .handlerName("test-handler")
                .aliasName("test-alias")
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

        topics.stream()
                .allMatch(topic -> oneMatches(output, abInputArn, abOutputArn, topic));
    }

    private boolean oneMatches(List<Subscription> subscriptions, String expectedTarget, String expectedSource, String expectedSubject) {
        return subscriptions.stream()
                .anyMatch(subscription -> subscription.source().equals(expectedSource) &&
                        subscription.subject().equals(expectedSubject) &&
                        subscription.target().equals(expectedTarget));
    }
}
