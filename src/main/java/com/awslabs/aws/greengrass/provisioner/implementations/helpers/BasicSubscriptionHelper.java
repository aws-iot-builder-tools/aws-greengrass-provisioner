package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.Subscription;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicSubscriptionHelper implements SubscriptionHelper {
    private final Logger log = LoggerFactory.getLogger(BasicSubscriptionHelper.class);
    @Inject
    IotHelper iotHelper;
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
        List<Subscription> subscriptions = new ArrayList<>();

        Map<String, List<String>> arnsByOutputTopic = new HashMap<>();
        Map<String, List<String>> arnsByInputTopic = new HashMap<>();

        // For each function
        for (Map.Entry<Function, FunctionConf> entry : functionAliasToConfMap.entrySet()) {
            // Loop through its output topics and put them in the map associated with their function ARN
            for (String outputTopic : entry.getValue().getOutputTopics()) {
                arnsByOutputTopic.computeIfAbsent(outputTopic, k -> new ArrayList<>()).add(entry.getKey().functionArn());
            }

            // Loop through its output topics and put them in the map associated with their function ARN
            for (String inputTopic : entry.getValue().getInputTopics()) {
                arnsByInputTopic.computeIfAbsent(inputTopic, k -> new ArrayList<>()).add(entry.getKey().functionArn());
            }
        }

        // For each GGD
        for (GGDConf ggdConf : ggdConfs) {
            // Get its thing ARN
            String thingArn = iotHelper.getThingArn(ggdConf.getThingName());

            // Loop through its output topics and put them in the map associated with their thing ARN
            for (String outputTopic : ggdConf.getOutputTopics()) {
                arnsByOutputTopic.computeIfAbsent(outputTopic, k -> new ArrayList<>()).add(thingArn);
            }

            // Loop through its input topics and put them in the map associated with their thing ARN
            for (String inputTopic : ggdConf.getInputTopics()) {
                arnsByInputTopic.computeIfAbsent(inputTopic, k -> new ArrayList<>()).add(thingArn);
            }
        }

        // Loop through all of the output topics we found
        for (Map.Entry<String, List<String>> entry : arnsByOutputTopic.entrySet()) {
            // Get the output topic name and the source ARNs for that topic
            String outputTopic = entry.getKey();
            List<String> sourceArns = entry.getValue();

            // Get the list of target ARNs for that topic
            List<String> targetArns = arnsByInputTopic.get(outputTopic);

            // Are there any target ARNs?
            if ((targetArns == null) || (targetArns.size() == 0)) {
                // No, move on
                continue;
            }

            // Yes, there are target ARNs for the topic.  Loop through all of the source ARNs.
            for (String sourceArn : sourceArns) {
                // Loop through all of the target ARNs
                for (String targetArn : targetArns) {
                    // Connect the source to the target
                    log.info("Connecting [" + sourceArn + "] to [" + targetArn + "] on topic [" + outputTopic + "]");
                    subscriptions.add(createSubscription(sourceArn, targetArn, outputTopic));
                }
            }
        }

        return subscriptions;
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
