package com.awslabs.aws.greengrass.provisioner.data;

import java.util.function.Function;

public enum DeviceTesterLogMessageType {
    CHECKING_GGC_VERSION(string -> string.equals("Checking whether version of Greengrass release is correct...")),
    RUNNING(string -> string.equals("Running test case...")),
    FINISHED(string -> string.equals("Finished running test case...")),
    PASS(string -> string.equals("PASS")),
    STARTING(string -> string.equals("Starting Greengrass...")),
    START(string -> string.equals("start Greengrass executed successfully.")),
    STOPPING(string -> string.equals("Stopping Greengrass...")),
    STOP(string -> string.equals("stop Greengrass executed successfully.")),
    PROVISIONING(string -> string.equals("Provisioning Greengrass...")),
    FINISHED_PROVISIONING(string -> string.equals("Finished provisioning Greengrass.")),
    CREATING_GGD(string -> string.equals("Creating GGD...")),
    FINISHED_CREATING_GGD(string -> string.equals("Finished creating GGD...")),
    PROVISIONING_GGD(string -> string.equals("Provisioning GGD...")),
    FINISHED_PROVISIONING_GGD(string -> string.equals("Finished provisioning GGD.")),
    CLEANING_UP(string -> string.equals("Cleaning up resources...")),
    FAIL_WITHOUT_DURATION(string -> string.equals("FAIL")),
    DEPLOYING_GROUP(string -> string.equals("Deploying group onto Greengrass core...")),
    CREATING_GREENGRASS_LAMBDAS(string -> string.equals("Creating Greengrass Lambda(s)...")),
    FINISHED_CREATING_GREENGRASS_LAMBDAS(string -> string.equals("Finished creating Greengrass Lambda(s).")),
    CREATING_GREENGRASS_GROUP(string -> string.equals("Creating a Greengrass group...")),
    FINISHED_DEPLOYING_GROUP(string -> string.equals("Finished deploying group onto Greengrass core.")),
    RESTARTING_GREENGRASS(string -> string.equals("Restarting Greengrass...")),
    RESTARTING_GREENGRASS_SUCCESSFUL(string -> string.equals("restart Greengrass executed successfully.")),
    ERRORS_WHEN_CLEANING_UP_RESOURCES(string -> string.equals("Errors when cleaning up resources: ")),
    RUNNING_GREENGRASS_ALREADY_INSTALLED(string -> string.equals("Running test with Greengrass already installed on your device at /greengrass...")),
    COULD_NOT_FIND_GREENGRASS_RELEASE(string -> string.equals("Could not find Greengrass release in the location provided \"/greengrass\" on device under test. Please confirm that the correct location was provided.")),
    // Partial matches
    FAIL_WITH_DURATION(string -> string.startsWith("--- FAIL: ")),
    ALL_TESTS_FINISHED(string -> string.startsWith(Constants.ALL_TESTS_FINISHED_MESSAGE)),
    REPORT_GENERATED(string -> string.startsWith(Constants.REPORT_GENERATED_MESSAGE)),
    TEST_TIMED_OUT(string -> string.startsWith("Test timed out")),
    TIMED_OUT(string -> string.startsWith("Timed out")),
    UNKNOWN_FAILURE(string -> string.startsWith("Failing")),
    XML_SYNTAX_ERROR(string -> string.startsWith("XML syntax error")),
    CLEANING_UP_RESOURCES_FAILED(string -> string.startsWith("Cleaning ") && string.contains("failed with error")),
    STATUS_CODE_ERROR(string -> string.contains("status code") && string.contains("request id")),
    CREDENTIALS_NOT_FOUND(string -> string.contains("credentials not found")),
    TEST_EXITED_UNSUCCESSFULLY(string -> string.contains("Test exited unsuccessfully")),
    FAIL_TO_REMOVE_GREENGRASS(string -> string.contains("Fail to remove Greengrass")),
    FAIL_TO_RESTORE_GREENGRASS(string -> string.contains("Fail to restore Greengrass")),
    COMMAND_ON_REMOTE_HOST_FAILED_TO_START(string -> string.contains("Async command on remote host failed to start with error")),
    FAIL_TO_ADD_REMOTE_FILE_RESOURCE(string -> string.contains("failed to add remote file resource")),
    EMPTY(string -> string.isEmpty());

    private final Function<String, Boolean> matcher;

    DeviceTesterLogMessageType(Function<String, Boolean> matcher) {
        this.matcher = matcher;
    }

    public boolean matches(String string) {
        return matcher.apply(string);
    }

    public static class Constants {
        public static final String ALL_TESTS_FINISHED_MESSAGE = "All tests finished. Aggregated report generated at the path: ";
        public static final String REPORT_GENERATED_MESSAGE = "AWS IoT Device Tester report generated at the path: ";
    }
}
