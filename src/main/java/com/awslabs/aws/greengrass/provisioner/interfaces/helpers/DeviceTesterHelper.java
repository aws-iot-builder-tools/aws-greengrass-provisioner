package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeviceTesterLogMessageType;
import io.vavr.collection.Map;
import io.vavr.control.Option;

public interface DeviceTesterHelper {
    String MESSAGE_FIELD_NAME = "msg";
    String TEST_CASE_ID = "testCaseId";

    /**
     * Determines the type of a log message
     *
     * @param logMessage
     * @return
     */
    DeviceTesterLogMessageType getLogMessageType(String logMessage);

    /**
     * Filters, reformats, and prints log messages from Device Tester
     *
     * @param logMessage
     */
    void log(String logMessage);

    /**
     * Extracts the test case ID, if present, from a log message that has been converted to a map
     *
     * @param values the test case ID or Optional.empty if it cannot be found
     * @return
     */
    Option<String> getOptionalTestCaseId(Map<String, String> values);

    /**
     * Converts a Device Tester log message into a map
     *
     * @param logMessage
     * @return
     */
    Map<String, String> extractValuesFromLogMessage(String logMessage);
}
