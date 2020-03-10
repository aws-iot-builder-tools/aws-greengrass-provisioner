package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGVariables;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.SubscriptionHelper;
import com.awslabs.iot.data.ImmutableThingName;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.Subscription;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BasicSubscriptionHelper implements SubscriptionHelper {
    private final Logger log = LoggerFactory.getLogger(BasicSubscriptionHelper.class);
    @Inject
    V2IotHelper v2IotHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    GGConstants ggConstants;
    @Inject
    GGVariables ggVariables;

    @Inject
    public BasicSubscriptionHelper() {
    }

    @Override
    public List<Subscription> connectFunctionsAndDevices(Map<Function, FunctionConf> functionAliasToConfMap, List<GGDConf> ggdConfs) {
        // Gather up the list of output topics and each function ARN that uses them
        Map<String, List<String>> outputTopicAndFunctionArnList = functionAliasToConfMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().getOutputTopics().stream().map(topic -> new Tuple2<>(topic, entry.getKey().functionArn())))
                .collect(Collectors.groupingBy(tuple -> tuple._1, Collectors.mapping(Tuple2::_2, Collectors.toList())));

        // Gather up the list of input topics and each function ARN that uses them
        Map<String, List<String>> inputTopicAndFunctionArnList = functionAliasToConfMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().getInputTopics().stream().map(topic -> new Tuple2<>(topic, entry.getKey().functionArn())))
                .collect(Collectors.groupingBy(tuple -> tuple._1, Collectors.mapping(Tuple2::_2, Collectors.toList())));

        // Gather up the list of output topics and each thing ARN (GGD) that uses them
        Map<String, List<String>> outputTopicAndThingArnList = ggdConfs.stream()
                .map(ggdConf -> new Tuple2<>(v2IotHelper.getThingArn(ImmutableThingName.builder().name(ggdConf.getThingName()).build()).get().getArn(), ggdConf))
                .flatMap(tuple -> tuple._2.getOutputTopics().stream().map(topic -> new Tuple2<>(topic, tuple._1)))
                .collect(Collectors.groupingBy(tuple -> tuple._1, Collectors.mapping(Tuple2::_2, Collectors.toList())));

        // Gather up the list of input topics and each thing ARN (GGD) that uses them
        Map<String, List<String>> inputTopicAndThingArnList = ggdConfs.stream()
                .map(ggdConf -> new Tuple2<>(v2IotHelper.getThingArn(ImmutableThingName.builder().name(ggdConf.getThingName()).build()).get().getArn(), ggdConf))
                .flatMap(tuple -> tuple._2.getInputTopics().stream().map(topic -> new Tuple2<>(topic, tuple._1)))
                .collect(Collectors.groupingBy(tuple -> tuple._1, Collectors.mapping(Tuple2::_2, Collectors.toList())));

        // Copy the maps
        Map<String, List<String>> outputTopicAndArnList = outputTopicAndFunctionArnList.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, List<String>> inputTopicAndArnList = inputTopicAndFunctionArnList.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Merge topic -> thing ARN maps with the topic -> function ARN maps
        outputTopicAndThingArnList.forEach((key, value) -> outputTopicAndArnList.merge(key, value, (v1, v2) -> Stream.concat(v1.stream(), v2.stream()).collect(Collectors.toList())));
        inputTopicAndThingArnList.forEach((key, value) -> inputTopicAndArnList.merge(key, value, (v1, v2) -> Stream.concat(v1.stream(), v2.stream()).collect(Collectors.toList())));

        // For each output topic see if there is a matching input topic candidate
        Set<String> outputTopics = outputTopicAndFunctionArnList.keySet();
        Set<String> inputTopics = inputTopicAndFunctionArnList.keySet();

        List<Tuple3<String, String, String>> outputInputTopicMappings = outputTopics.stream()
                .flatMap(outputTopic -> inputTopics.stream().map(inputTopic -> new Tuple3<>(outputTopic, inputTopic, topicCandidate(outputTopic, inputTopic))))
                .filter(tuple3 -> tuple3._3.isPresent())
                .map(tuple3 -> new Tuple3<>(tuple3._1, tuple3._2, tuple3._3.get()))
                .collect(Collectors.toList());

        List<Tuple3<List<String>, List<String>, String>> sourceToTargetMappings = outputInputTopicMappings.stream()
                .map(tuple3 -> new Tuple3<>(outputTopicAndArnList.get(tuple3._1), inputTopicAndArnList.get(tuple3._2), tuple3._3))
                .collect(Collectors.toList());

        List<Subscription> subscriptions = sourceToTargetMappings.stream()
                .flatMap(entry -> entry._1.stream()
                        .flatMap(outputTopic -> entry._2.stream()
                                .map(inputTopic -> createSubscription(outputTopic, inputTopic, entry._3))))
                .collect(Collectors.toList());

        subscriptions.forEach(subscription ->
                log.info("Connecting [" + subscription.source() + "] to [" + subscription.target() + "] on topic [" + subscription.subject() + "]"));

        return subscriptions;
    }

    protected Optional<String> topicCandidate(String topic1, String topic2) {
        List<String> splitTopic1 = Arrays.asList(topic1.split("/"));
        List<String> splitTopic2 = Arrays.asList(topic2.split("/"));
        int splitTopic1Length = splitTopic1.size();
        int splitTopic2Length = splitTopic2.size();

        int shortestLength = Math.min(splitTopic1Length, splitTopic2Length);

        List<String> output = new ArrayList<>();

        for (int loop = 0; loop < shortestLength - 1; loop++) {
            String input1 = splitTopic1.get(loop);
            String input2 = splitTopic2.get(loop);

            if (isMultilevelWildcard(input1) || isMultilevelWildcard(input2)) {
                // Input 1 or 2 contains a multilevel wildcard in the middle, this is an error
                throw new RuntimeException("Invalid pattern #1, multilevel wildcards can only be used at the last topic hierarchy level");
            }

            Optional<String> optionalSingleTopicLevel = getSingleTopicLevel(input1, input2);

            if (!optionalSingleTopicLevel.isPresent()) {
                // No match
                return Optional.empty();
            }

            output.add(optionalSingleTopicLevel.get());
        }

        // This is the final level for one or both of these
        int finalIndex = shortestLength - 1;

        String input1 = splitTopic1.get(finalIndex);
        String input2 = splitTopic2.get(finalIndex);

        boolean input1End = ((splitTopic1Length - 1) == finalIndex);
        boolean input2End = ((splitTopic2Length - 1) == finalIndex);

        if (input1End && input2End && input1.equals(input2)) {
            // Both are at their last level and both are equal, use the last level of input 1
            output.add(input1);

            return outputCandidate(output);
        }

        if (isSingleLevelWildcard(input1)) {
            if (!input2End) {
                // Input 1 is a single level wildcard but input 2 has more than one level left, no match
                return Optional.empty();
            }

            // Use the final level of input 2
            output.add(input2);

            return outputCandidate(output);
        }

        if (isSingleLevelWildcard(input2)) {
            if (!input1End) {
                // Input 2 is a single level wildcard but input 1 has more than one level left, no match
                return Optional.empty();
            }

            // Use the final level of input 1
            output.add(input1);

            return outputCandidate(output);
        }

        boolean input1MultilevelWildcard = isMultilevelWildcard(input1);
        boolean input2MultilevelWildcard = isMultilevelWildcard(input2);

        if (input1MultilevelWildcard && !input1End) {
            // Input 1 contains a multilevel wildcard but it isn't at the end, this is an error
            throw new RuntimeException("Invalid pattern #2, multilevel wildcards can only be used at the last topic hierarchy level");
        }

        if (input2MultilevelWildcard && !input2End) {
            // Input 2 contains a multilevel wildcard but it isn't at the end, this is an error
            throw new RuntimeException("Invalid pattern #3, multilevel wildcards can only be used at the last topic hierarchy level");
        }

        if (input1MultilevelWildcard && input2MultilevelWildcard) {
            // Both end in a multilevel wildcard, use a multilevel wildcard as the final part of the topic
            output.add("#");

            return outputCandidate(output);
        }

        if (input1MultilevelWildcard) {
            // Input 1 ends in a multilevel wildcard as its very last level, use the rest of the input 2 topic
            output.addAll(splitTopic2.subList(finalIndex, splitTopic2Length));

            return outputCandidate(output);
        }

        if (input2MultilevelWildcard) {
            // Input 2 ends in a multilevel wildcard as its very last level, use the rest of the input 1 topic
            output.addAll(splitTopic1.subList(finalIndex, splitTopic1Length));

            return outputCandidate(output);
        }

        // Doesn't look like a match
        return Optional.empty();
    }

    private Optional<String> outputCandidate(List<String> output) {
        return Optional.of(String.join("/", output));
    }

    private Optional<String> getSingleTopicLevel(String input1, String input2) {
        if (input1.equals(input2)) {
            // Both are the same value, use the input 1 value
            return Optional.of(input1);
        }

        if (isSingleLevelWildcard(input1) && isSingleLevelWildcard(input2)) {
            // Both are single level wildcards, put a single level wildcard
            return Optional.of(input1);
        }

        if (isSingleLevelWildcard(input1)) {
            // Input 1 is a wildcard, make the output more strict by using the strict value of input 2
            return Optional.of(input2);
        }

        if (isSingleLevelWildcard(input2)) {
            // Input 2 is a wildcard, make the output more strict by using the strict value of input 1
            return Optional.of(input1);
        }

        // At this point the values aren't equal and aren't wildcards, this isn't a match
        return Optional.empty();
    }

    private boolean isSingleLevelWildcard(String input) {
        return "+".equals(input);
    }

    private boolean isMultilevelWildcard(String input) {
        return "#".equals(input);
    }

    private boolean isWildcard(String input) {
        return isSingleLevelWildcard(input) || isMultilevelWildcard(input);
    }

    @Override
    public List<Subscription> connectFunctionsToShadows(Map<Function, FunctionConf> functionAliasToConfMap) {
        List<Subscription> subscriptions = new ArrayList<>();

        for (Map.Entry<Function, FunctionConf> entry : functionAliasToConfMap.entrySet()) {
            String functionArn = entry.getKey().functionArn();
            FunctionConf functionConf = entry.getValue();

            for (String deviceName : functionConf.getConnectedShadows()) {
                subscriptions.addAll(createShadowSubscriptions(functionArn, deviceName));
            }
        }

        return subscriptions;
    }

    @Override
    public List<Subscription> createCloudSubscriptionsForArn(List<String> fromCloudSubscriptions, List<String> toCloudSubscriptions, String arn) {
        List<Subscription> subscriptions = new ArrayList<>();

        for (String subscriptionToCloud : toCloudSubscriptions) {
            log.info("- Creating subscription to cloud [" + arn + "] to [" + subscriptionToCloud + "]");
            subscriptions.add(createSubscriptionToCloud(arn, subscriptionToCloud));
        }

        for (String subscriptionFromCloud : fromCloudSubscriptions) {
            log.info("- Creating subscription from cloud [" + arn + "] to [" + subscriptionFromCloud + "]");
            subscriptions.add(createSubscriptionFromCloud(arn, subscriptionFromCloud));
        }

        return subscriptions;
    }

    @Override
    public Subscription createSubscription(String source, String target, String topicFilter) {
        return Subscription.builder()
                .id(ioHelper.getUuid())
                .source(source)
                .subject(topicFilter)
                .target(target)
                .build();
    }

    @Override
    public Subscription createSubscriptionToCloud(String source, String topicFilter) {
        return createSubscription(source, CLOUD, topicFilter);
    }

    @Override
    public Subscription createSubscriptionFromCloud(String target, String topicFilter) {
        return createSubscription(CLOUD, target, topicFilter);
    }

    @Override
    public List<Subscription> createShadowSubscriptions(String deviceOrFunctionArn, String deviceThingName) {
        List<Subscription> subscriptions = new ArrayList<>();

        Subscription shadowServiceTargetSubscription = Subscription.builder()
                .id(ioHelper.getUuid())
                .source(deviceOrFunctionArn)
                .subject(ggVariables.getDeviceShadowTopicFilterName(deviceThingName))
                .target(ggConstants.getGgShadowServiceName())
                .build();

        subscriptions.add(shadowServiceTargetSubscription);

        Subscription shadowServiceSourceSubscription = Subscription.builder()
                .id(ioHelper.getUuid())
                .source(ggConstants.getGgShadowServiceName())
                .subject(ggVariables.getDeviceShadowTopicFilterName(deviceThingName))
                .target(deviceOrFunctionArn)
                .build();

        subscriptions.add(shadowServiceSourceSubscription);

        return subscriptions;
    }
}
