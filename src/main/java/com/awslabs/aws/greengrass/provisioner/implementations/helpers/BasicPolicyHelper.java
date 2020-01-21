package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.PolicyHelper;
import com.awslabs.aws.iot.resultsiterator.helpers.interfaces.JsonHelper;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class BasicPolicyHelper implements PolicyHelper {
    @Inject
    JsonHelper jsonHelper;

    @Inject
    public BasicPolicyHelper() {
    }

    @Override
    public String buildDevicePolicyDocument(String deviceThingArn) {
        Map<String, Object> map = new HashMap();
        map.put("Version", "2012-10-17");

        String[] iotActions = new String[]{"iot:Connect", "iot:Publish", "iot:Subscribe", "iot:Receive"};

        Map iotStatementMap = new HashMap();
        iotStatementMap.put("Action", iotActions);
        iotStatementMap.put("Effect", "Allow");
        iotStatementMap.put("Resource", new String[]{"*"});

        String[] ggActions = new String[]{"greengrass:Discover"};

        Map ggStatementMap = new HashMap();
        ggStatementMap.put("Action", ggActions);
        ggStatementMap.put("Effect", "Allow");
        ggStatementMap.put("Resource", new String[]{deviceThingArn});

        Map[] statements = new Map[]{iotStatementMap, ggStatementMap};

        map.put("Statement", statements);

        return jsonHelper.toJson(map);
    }
}
