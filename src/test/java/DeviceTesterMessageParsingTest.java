import com.awslabs.aws.greengrass.provisioner.data.DeviceTesterLogMessageType;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicDeviceTesterHelper;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DeviceTesterMessageParsingTest {
    private static String CHECKING_GGC_VERSION_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:02-05:00\" level=info msg=Checking whether version of Greengrass release is correct... executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=version testCaseId=ggc_version_check_test_1 deviceId=DUT";

    private static String RUNNING_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:01-05:00\" level=info msg=Running test case... testCaseId=ggc_version_check_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=version";
    private static String RUNNING_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:02-05:00\" level=info msg=Running test case... suiteId=GGQ groupId=dependencies testCaseId=cgroups_check_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d";

    private static String FINISHED_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:02-05:00\" level=info msg=Finished running test case... executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=version testCaseId=ggc_version_check_test_1 deviceId=DUT";
    private static String FINISHED_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:05-05:00\" level=info msg=Finished running test case... testCaseId=cgroups_check_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=dependencies";

    private static String PASS_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:02-05:00\" level=info msg=PASS deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=version testCaseId=ggc_version_check_test_1";
    private static String PASS_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:05-05:00\" level=info msg=PASS groupId=dependencies testCaseId=cgroups_check_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ";

    private static String STARTING_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:19-05:00\" level=info msg=Starting Greengrass... groupId=ipd testCaseId=ipd_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ";
    private static String STARTING_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:24-05:00\" level=info msg=Starting Greengrass... deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_1";

    private static String START_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T12:42:17-05:00\" level=info msg=start Greengrass executed successfully. deviceId=DUT executionId=f8c25977-14fe-11e9-9f20-9801a78f161d suiteId=GGQ groupId=penetration testCaseId=penetration_test_4";

    private static String STOPPING_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:20-05:00\" level=info msg=Stopping Greengrass... executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_1 deviceId=DUT";
    private static String STOPPING_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:20-05:00\" level=info msg=Stopping Greengrass... suiteId=GGQ groupId=ipd testCaseId=ipd_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d";

    private static String STOP_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:20-05:00\" level=info msg=stop Greengrass executed successfully. executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_1 deviceId=DUT";
    private static String STOP_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:20-05:00\" level=info msg=stop Greengrass executed successfully. testCaseId=ipd_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd";

    private static String PROVISIONING_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:22-05:00\" level=info msg=Provisioning Greengrass... suiteId=GGQ groupId=ipd testCaseId=ipd_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d";
    private static String PROVISIONING_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:28-05:00\" level=info msg=Provisioning Greengrass... executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_2 deviceId=DUT";

    private static String FINISHED_PROVISIONING_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:24-05:00\" level=info msg=Finished provisioning Greengrass. executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_1 deviceId=DUT";
    private static String FINISHED_PROVISIONING_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:29-05:00\" level=info msg=Finished provisioning Greengrass. suiteId=GGQ groupId=ipd testCaseId=ipd_test_2 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d";

    private static String CREATING_GGD_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T12:45:43-05:00\" level=info msg=Creating GGD... deviceId=DUT executionId=8148e130-14ff-11e9-8117-9801a78f161d suiteId=GGQ groupId=lra testCaseId=lra_test_1";

    private static String FINISHED_CREATING_GGD_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T12:49:40-05:00\" level=info msg=Finished creating GGD... executionId=080ee1ed-1500-11e9-94e9-9801a78f161d suiteId=GGQ groupId=lra testCaseId=lra_test_1 deviceId=DUT";

    private static String PROVISIONING_GGD_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T13:56:58-05:00\" level=info msg=Provisioning GGD... suiteId=GGQ groupId=combination testCaseId=sec4_test_1 deviceId=DUT executionId=6ef56e02-1509-11e9-b62e-9801a78f161d";

    private static String FINISHED_PROVISIONING_GGD_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T14:01:59-05:00\" level=info msg=Finished provisioning GGD. suiteId=GGQ groupId=tes testCaseId=tes_test_1 deviceId=DUT executionId=17f642d5-150a-11e9-a46d-9801a78f161d";

    private static String CLEANING_UP_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:26-05:00\" level=info msg=Cleaning up resources... suiteId=GGQ groupId=ipd testCaseId=ipd_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d";
    private static String CLEANING_UP_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:39-05:00\" level=info msg=Cleaning up resources... suiteId=GGQ groupId=ipd testCaseId=ipd_test_2 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d";

    private static String FAIL_WITH_DURATION_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:27-05:00\" level=info msg=--- FAIL: TestIPD (10.28s) testCaseId=ipd_test_1 deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd";
    private static String FAIL_WITH_DURATION_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:39-05:00\" level=info msg=--- FAIL: TestIPD (12.39s) deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_2";

    private static String FAIL_WITHOUT_DURATION_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:27-05:00\" level=info msg=FAIL deviceId=DUT executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_1";
    private static String FAIL_WITHOUT_DURATION_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T08:55:39-05:00\" level=info msg=FAIL executionId=52cf78b2-14df-11e9-919c-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_2 deviceId=DUT";

    private static String ALL_TESTS_FINISHED_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T09:18:31-05:00\" level=info msg=All tests finished. Aggregated report generated at the path: /private/var/folders/9c/tpt08_m11dx1rfdl906jcdyn5l_v81/T/1547128426653-0/devicetester_greengrass_mac/results/52cf78b2-14df-11e9-919c-9801a78f161d/GGQ_Report.xml executionId=52cf78b2-14df-11e9-919c-9801a78f161d";

    private static String REPORT_GENERATED_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T09:18:31-05:00\" level=info msg=AWS IoT Device Tester report generated at the path: /private/var/folders/9c/tpt08_m11dx1rfdl906jcdyn5l_v81/T/1547128426653-0/devicetester_greengrass_mac/results/52cf78b2-14df-11e9-919c-9801a78f161d/awsiotdevicetester_report.xml poolId=DevicePool suiteId=GGQ executionId=52cf78b2-14df-11e9-919c-9801a78f161d";

    private static String UNKNOWN_FAILURE_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T15:55:18-05:00\" level=info msg=Failing copying the busybox to target device. failed to create file at path busybox-armv7l: wait: remote command exited without exit status or exit signal executionId=259b115e-1519-11e9-ba64-9801a78f161d suiteId=GGQ groupId=dcm testCaseId=dcm_test_1 deviceId=DUT";

    private static String DEPLOYING_GROUP_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T17:09:30-05:00\" level=info msg=Deploying group onto Greengrass core... executionId=5532254b-1524-11e9-b8f6-9801a78f161d suiteId=GGQ groupId=deployment testCaseId=deployment_test_1 deviceId=DUT";

    private static String CREATING_GREENGRASS_LAMBDAS_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T17:25:57-05:00\" level=info msg=Creating Greengrass Lambda(s)... groupId=ipd testCaseId=ipd_test_1 deviceId=DUT executionId=eb3d6c74-1524-11e9-a415-9801a78f161d suiteId=GGQ";

    private static String TIMED_OUT_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-10T20:15:31-05:00\" level=error msg=Timed out initializing remote shell at pi@192.168.1.29:22: failed to connect to target device <192.168.1.29>: dial tcp 192.168.1.29:22: i/o timeout suiteId=GGQ groupId=dcm testCaseId=dcm_test_1 deviceId=DUT executionId=9d582aa8-153d-11e9-a623-9801a78f161d";

    private static String FINISHED_CREATING_GREENGRASS_LAMBDAS_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-11T11:05:47-05:00\" level=info msg=Finished creating Greengrass Lambda(s). executionId=51a01bf8-15ba-11e9-88da-9801a78f161d suiteId=GGQ groupId=penetration testCaseId=penetration_test_5 deviceId=DUT]";

    private static String CREATING_GREENGRASS_GROUP_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-11T11:59:30-05:00\" level=info msg=Creating a Greengrass group... executionId=244114fb-15c2-11e9-bc05-9801a78f161d suiteId=GGQ groupId=combination testCaseId=sec4_test_1 deviceId=DUT";

    private static String FINISHED_DEPLOYING_GROUP_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-11T12:18:59-05:00\" level=info msg=Finished deploying group onto Greengrass core. executionId=e1c52349-15c4-11e9-8f20-9801a78f161d suiteId=GGQ groupId=ipd testCaseId=ipd_test_1 deviceId=DUT";

    private static String RESTARTING_GREENGRASS_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-11T12:28:36-05:00\" level=info msg=Restarting Greengrass... suiteId=GGQ groupId=dcm testCaseId=dcm_test_1 deviceId=DUT executionId=3430c41e-15c6-11e9-bb1b-9801a78f161d]";

    private static String TEST_TIMED_OUT_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-11T13:23:14-05:00\" level=warning msg=Test timed out executionId=5bdeb609-15cb-11e9-b932-9801a78f161d testCaseId=lra_test_1";

    private static String XML_SYNTAX_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-11T16:31:39-05:00\" level=error msg=XML syntax error on line 2: illegal character code U+0000";

    private static String RESTARTING_GREENGRASS_SUCCESSFUL_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-11T17:15:37-05:00\" level=info msg=restart Greengrass executed successfully. suiteId=GGQ groupId=ipd testCaseId=ipd_test_2 deviceId=DUT executionId=2e8451ab-15ee-11e9-80e7-9801a78f161d";

    private static String ERRORS_WHEN_CLEANING_UP_RESOURCES_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-14T17:46:52-05:00\" level=info msg=Errors when cleaning up resources:  groupId=shadow testCaseId=shadow_sync_test_1 deviceId=DUT executionId=95597c02-184c-11e9-b100-9801a78f161d suiteId=GGQ";
    private static String ERRORS_WHEN_CLEANING_UP_RESOURCES_2 = "[INFO] BasicGroupTestHelper: time=\"2019-01-14T17:46:52-05:00\" level=info msg=Errors when cleaning up resources:  groupId=shadow testCaseId=shadow_sync_test_1 deviceId=DUT executionId=95597c02-184c-11e9-b100-9801a78f161d suiteId=GGQ";

    private static String RUNNING_GREEGRASS_ALREADY_INSTALLED_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-14T16:54:59-05:00\" level=info msg=Running test with Greengrass already installed on your device at /greengrass... groupId=ipd testCaseId=ipd_test_1 deviceId=DUT executionId=f4603351-1846-11e9-a6ad-9801a78f161d suiteId=GGQ";

    private static String COULD_NOT_FIND_GREENGRASS_RELEASE_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-15T09:45:10-05:00\" level=info msg=Could not find Greengrass release in the location provided \"/greengrass\" on device under test. Please confirm that the correct location was provided. executionId=27ff9f33-18d4-11e9-9205-9801a78f161d suiteId=GGQ groupId=version testCaseId=ggc_version_check_test_1 deviceId=DUT";

    private static String CLEANING_UP_RESOURCES_FAILED_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-15T23:59:54-05:00\" level=info msg=Cleaning *resources.thing failed with error InvalidRequestException: Cannot delete. Thing idt-5887755929761195752 is still attached to one or more principals groupId=lra testCaseId=lra_test_1 deviceId=DUT executionId=f062a7a3-1947-11e9-84e1-9801a78f161d suiteId=GGQ";

    private static String STATUS_CODE_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-01-16T11:35:19-05:00\" level=info msg=\tstatus code: 400, request id: b4f783c9-19ac-11e9-8858-5b08ca276463 executionId=65dd3611-19ab-11e9-83ea-9801a78f161d suiteId=GGQ groupId=tes testCaseId=tes_test_1 deviceId=DUT";

    private static String CREDENTIALS_NOT_FOUND_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-04-08T14:30:21Z\" level=error msg=aws credentials not found: EnvAccessKeyNotFound: AWS_ACCESS_KEY_ID or AWS_ACCESS_KEY not found in environment testCaseId=ggc_version_check_test_1 deviceId=DUT executionId=d6bba8c9-5a0a-11e9-b94f-0242ac110002 suiteId=GGQ groupId=version";

    private static String TEST_EXITED_UNSUCCESSFULLY_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-04-08T14:45:57Z\" level=error msg=Test exited unsuccessfully executionId=0495e067-5a0d-11e9-b72c-0242ac110003 testCaseId=ggc_version_check_test_1 error=exit status 1";

    private static String EMPTY_1 = "[INFO] BasicGroupTestHelper: time=\"2019-04-08T14:45:57Z\" level=info msg=";

    private static String FAIL_TO_REMOVE_GREENGRASS_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-04-29T13:57:51-04:00\" level=info msg=Fail to remove Greengrass at /greengrass: [Error: 101] InternalError: failed to run command async with stdio: read tcp 10.95.232.31:51563->3.82.230.146:22: read: operation timed out executionId=98b2397b-6aa6-11e9-8cc9-9801a78f161d suiteId=GGQ groupId= testCaseId=dcm_test_1 deviceId=DUT";

    private static String FAIL_TO_RESTORE_GREENGRASS_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-04-29T15:39:45-04:00\" level=info msg=Fail to restore Greengrass to initial state: [Error: 101] InternalError: failed to restore pre-installed greengrass to its initial state: failed to run command async with stdio: read tcp 10.95.232.31:56949->3.82.230.146:22: read: operation timed out deviceId=DUT executionId=1f819844-6ab3-11e9-a2d5-9801a78f161d suiteId=GGQ groupId= testCaseId=tes_test_1";

    private static String COMMAND_ON_REMOTE_HOST_FAILED_TO_START_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-04-30T17:40:38Z\" level=error msg=panic: Async command on remote host failed to start with error: EOF groupId= testCaseId=shadow_sync_test_1 deviceId=DUT executionId=c875296d-6b6d-11e9-99df-0242ac110002 suiteId=GGQ";

    private static String FAIL_TO_ADD_REMOTE_FILE_RESOURCE_ERROR_1 = "[INFO] BasicGroupTestHelper: time=\"2019-06-05T18:02:47Z\" level=info msg=[Error: 101] InternalError: failed to add remote file resource: failed to copy file to target path busybox-x86_64: failed to create parent directory at path busybox-x86_64: Process exited with status 1 suiteId=GGQ groupId=version testCaseId=ggc_version_check_test_1 deviceId=DUT executionId=1e987a3f-87bc-11e9-ac9c-0242ac110002";

    private static List<String> CHECKING_GGC_VERSION_STRINGS = List.of(CHECKING_GGC_VERSION_1);
    private static List<String> RUNNING_STRINGS = List.of(RUNNING_1, RUNNING_2);
    private static List<String> FINISHED_STRINGS = List.of(FINISHED_1, FINISHED_2);
    private static List<String> PASS_STRINGS = List.of(PASS_1, PASS_2);
    private static List<String> STARTING_STRINGS = List.of(STARTING_1, STARTING_2);
    private static List<String> START_STRINGS = List.of(START_1);
    private static List<String> STOPPING_STRINGS = List.of(STOPPING_1, STOPPING_2);
    private static List<String> STOP_STRINGS = List.of(STOP_1, STOP_2);
    private static List<String> PROVISIONING_STRINGS = List.of(PROVISIONING_1, PROVISIONING_2);
    private static List<String> FINISHED_PROVISIONING_STRINGS = List.of(FINISHED_PROVISIONING_1, FINISHED_PROVISIONING_2);
    private static List<String> CREATING_GGD_STRINGS = List.of(CREATING_GGD_1);
    private static List<String> FINISHED_CREATING_GGD_STRINGS = List.of(FINISHED_CREATING_GGD_1);
    private static List<String> PROVISIONING_GGD_STRINGS = List.of(PROVISIONING_GGD_1);
    private static List<String> FINISHED_PROVISIONING_GGD_STRINGS = List.of(FINISHED_PROVISIONING_GGD_1);
    private static List<String> CLEANING_UP_STRINGS = List.of(CLEANING_UP_1, CLEANING_UP_2);
    private static List<String> FAIL_WITH_DURATION_STRINGS = List.of(FAIL_WITH_DURATION_1, FAIL_WITH_DURATION_2);
    private static List<String> FAIL_WITHOUT_DURATION_STRINGS = List.of(FAIL_WITHOUT_DURATION_1, FAIL_WITHOUT_DURATION_2);
    private static List<String> ALL_TESTS_FINISHED_STRINGS = List.of(ALL_TESTS_FINISHED_1);
    private static List<String> REPORT_GENERATED_STRINGS = List.of(REPORT_GENERATED_1);
    private static List<String> UNKNOWN_FAILURE_STRINGS = List.of(UNKNOWN_FAILURE_1);
    private static List<String> DEPLOYING_GROUP_STRINGS = List.of(DEPLOYING_GROUP_1);
    private static List<String> CREATING_GREENGRASS_LAMBDAS_STRINGS = List.of(CREATING_GREENGRASS_LAMBDAS_1);
    private static List<String> TIMED_OUT_STRINGS = List.of(TIMED_OUT_1);
    private static List<String> FINISHED_CREATING_GREENGRASS_LAMBDAS_STRINGS = List.of(FINISHED_CREATING_GREENGRASS_LAMBDAS_1);
    private static List<String> CREATING_GREENGRASS_GROUP_STRINGS = List.of(CREATING_GREENGRASS_GROUP_1);
    private static List<String> FINISHED_DEPLOYING_GROUP_STRINGS = List.of(FINISHED_DEPLOYING_GROUP_1);
    private static List<String> RESTARTING_GREENGRASS_STRINGS = List.of(RESTARTING_GREENGRASS_1);
    private static List<String> TEST_TIMED_OUT_STRINGS = List.of(TEST_TIMED_OUT_1);
    private static List<String> XML_SYNTAX_ERROR_STRINGS = List.of(XML_SYNTAX_ERROR_1);
    private static List<String> RESTARTING_GREENGRASS_SUCCESSFUL_STRINGS = List.of(RESTARTING_GREENGRASS_SUCCESSFUL_1);
    private static List<String> ERRORS_WHEN_CLEANING_UP_RESOURCES_STRINGS = List.of(ERRORS_WHEN_CLEANING_UP_RESOURCES_1, ERRORS_WHEN_CLEANING_UP_RESOURCES_2);
    private static List<String> RUNNING_GREENGRASS_ALREADY_INSTALLED_STRINGS = List.of(RUNNING_GREEGRASS_ALREADY_INSTALLED_1);
    private static List<String> COULD_NOT_FIND_GREENGRASS_RELEASE_STRINGS = List.of(COULD_NOT_FIND_GREENGRASS_RELEASE_1);
    private static List<String> CLEANING_UP_RESOURCES_FAILED_STRINGS = List.of(CLEANING_UP_RESOURCES_FAILED_1);
    private static List<String> STATUS_CODE_ERROR_STRINGS = List.of(STATUS_CODE_ERROR_1);
    private static List<String> CREDENTIALS_NOT_FOUND_STRINGS = List.of(CREDENTIALS_NOT_FOUND_ERROR_1);
    private static List<String> TEST_EXITED_UNSUCCESSFULLY_STRINGS = List.of(TEST_EXITED_UNSUCCESSFULLY_ERROR_1);
    private static List<String> FAIL_TO_REMOVE_GREENGRASS_STRINGS = List.of(FAIL_TO_REMOVE_GREENGRASS_ERROR_1);
    private static List<String> FAIL_TO_RESTORE_GREENGRASS_STRINGS = List.of(FAIL_TO_RESTORE_GREENGRASS_ERROR_1);
    private static List<String> COMMAND_ON_REMOTE_HOST_FAILED_TO_START_STRINGS = List.of(COMMAND_ON_REMOTE_HOST_FAILED_TO_START_ERROR_1);
    private static List<String> FAIL_TO_ADD_REMOTE_FILE_RESOURCE_STRINGS = List.of(FAIL_TO_ADD_REMOTE_FILE_RESOURCE_ERROR_1);
    private static List<String> EMPTY_STRINGS = List.of(EMPTY_1);

    private static List<String> ALL_STRINGS = List.of(
            CHECKING_GGC_VERSION_STRINGS,
            RUNNING_STRINGS,
            FINISHED_STRINGS,
            PASS_STRINGS,
            STARTING_STRINGS,
            START_STRINGS,
            STOPPING_STRINGS,
            STOP_STRINGS,
            PROVISIONING_STRINGS,
            FINISHED_PROVISIONING_STRINGS,
            CREATING_GGD_STRINGS,
            FINISHED_CREATING_GGD_STRINGS,
            PROVISIONING_GGD_STRINGS,
            FINISHED_PROVISIONING_GGD_STRINGS,
            CLEANING_UP_STRINGS,
            FAIL_WITH_DURATION_STRINGS,
            FAIL_WITHOUT_DURATION_STRINGS,
            ALL_TESTS_FINISHED_STRINGS,
            REPORT_GENERATED_STRINGS,
            UNKNOWN_FAILURE_STRINGS,
            DEPLOYING_GROUP_STRINGS,
            CREATING_GREENGRASS_LAMBDAS_STRINGS,
            TIMED_OUT_STRINGS,
            FINISHED_CREATING_GREENGRASS_LAMBDAS_STRINGS,
            CREATING_GREENGRASS_GROUP_STRINGS,
            FINISHED_DEPLOYING_GROUP_STRINGS,
            RESTARTING_GREENGRASS_STRINGS,
            TEST_TIMED_OUT_STRINGS,
            XML_SYNTAX_ERROR_STRINGS,
            RESTARTING_GREENGRASS_SUCCESSFUL_STRINGS,
            ERRORS_WHEN_CLEANING_UP_RESOURCES_STRINGS,
            RUNNING_GREENGRASS_ALREADY_INSTALLED_STRINGS,
            COULD_NOT_FIND_GREENGRASS_RELEASE_STRINGS,
            CLEANING_UP_RESOURCES_FAILED_STRINGS,
            STATUS_CODE_ERROR_STRINGS,
            CREDENTIALS_NOT_FOUND_STRINGS,
            TEST_EXITED_UNSUCCESSFULLY_STRINGS,
            FAIL_TO_REMOVE_GREENGRASS_STRINGS,
            FAIL_TO_RESTORE_GREENGRASS_STRINGS,
            COMMAND_ON_REMOTE_HOST_FAILED_TO_START_STRINGS,
            FAIL_TO_ADD_REMOTE_FILE_RESOURCE_STRINGS,
            EMPTY_STRINGS)
            .flatMap(List::toStream);

    private BasicDeviceTesterHelper basicDeviceTesterHelper;

    @Before
    public void setup() {
        basicDeviceTesterHelper = new BasicDeviceTesterHelper();
    }

    @Test
    public void shouldModifyAllStringsWhenTrimmed() {
        // Validates that the trimming regex isn't broken
        ALL_STRINGS.toStream()
                .forEach(string -> Assert.assertNotEquals(string, trimJavaLoggerInfo(string)));
    }

    @Test
    public void shouldFindAllMessageTypes() {
        Set<DeviceTesterLogMessageType> deviceTesterLogMessageTypes = ALL_STRINGS.toStream()
                .map(this::trimJavaLoggerInfo)
                .map(basicDeviceTesterHelper::getLogMessageType)
                .toSet();

        if (DeviceTesterLogMessageType.values().length == deviceTesterLogMessageTypes.length()) {
            return;
        }

        List<DeviceTesterLogMessageType> missing = List.of(DeviceTesterLogMessageType.values()).removeAll(deviceTesterLogMessageTypes.toJavaSet());

        missing.toStream()
                .forEach(deviceTesterLogMessageType -> System.out.println(String.format("Did not find a message with type [" + deviceTesterLogMessageType.name() + "]")));

        Assert.fail("Did not identify all message types");
    }

    @Test
    public void shouldExtractFieldsFromAllMessages() {
        List<Map<String, String>> values = ALL_STRINGS.toStream()
                .map(this::trimJavaLoggerInfo)
                .map(basicDeviceTesterHelper::extractValuesFromLogMessage)
                .toList();

        values.toStream()
                .forEach(map -> Assert.assertTrue(map.size() != 0));
    }

    private String trimJavaLoggerInfo(String string) {
        return string.replaceFirst("\\[INFO\\] BasicGroupTestHelper: ", "");
    }

    @Test
    public void shouldNotThrowExceptionWhenLoggingAllMessageTypes() {
        ALL_STRINGS.toStream()
                .forEach(basicDeviceTesterHelper::log);
    }
}
