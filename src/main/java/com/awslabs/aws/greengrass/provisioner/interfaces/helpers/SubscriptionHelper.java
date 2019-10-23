package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.data.conf.GGDConf;
import software.amazon.awssdk.services.greengrass.model.Function;
import software.amazon.awssdk.services.greengrass.model.Subscription;

import java.util.List;
import java.util.Map;

public interface SubscriptionHelper {
    String CLOUD = "cloud";

    Subscription createSubscription(String source, String target, String topicFilter);

    Subscription createSubscriptionToCloud(String source, String topicFilter);

    Subscription createSubscriptionFromCloud(String target, String topicFilter);

    List<Subscription> connectFunctionsAndDevices(Map<Function, FunctionConf> functionAliasToConfMap, List<GGDConf> ggdConfs);

    List<Subscription> connectFunctionsToShadows(Map<Function, FunctionConf> functionAliasToConfMap);

    List<Subscription> createCloudSubscriptionsForArn(List<String> fromCloudSubscriptions, List<String> toCloudSubscriptions, String arn);

    List<Subscription> createShadowSubscriptions(String deviceOrFunctionArn, String deviceThingName);
}
