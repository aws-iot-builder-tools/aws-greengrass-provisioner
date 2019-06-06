package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.DeviceTesterLogMessageType;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.DeviceTesterHelper;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vavr.API.*;

@Slf4j
public class BasicDeviceTesterHelper implements DeviceTesterHelper {
    public static final String TIME = "time";
    private static final String KEY_PATTERN = "[a-zA-Z]+=";
    private static final List<DeviceTesterLogMessageType> IGNORED_MESSAGE_TYPES = List.of(
            DeviceTesterLogMessageType.CHECKING_GGC_VERSION,
            DeviceTesterLogMessageType.FINISHED_PROVISIONING,
            DeviceTesterLogMessageType.CREATING_GGD,
            DeviceTesterLogMessageType.FINISHED_CREATING_GGD,
            DeviceTesterLogMessageType.PROVISIONING_GGD,
            DeviceTesterLogMessageType.FINISHED_PROVISIONING_GGD,
            DeviceTesterLogMessageType.STARTING,
            DeviceTesterLogMessageType.START,
            DeviceTesterLogMessageType.STOPPING,
            DeviceTesterLogMessageType.STOP,
            DeviceTesterLogMessageType.FAIL_WITHOUT_DURATION,
            DeviceTesterLogMessageType.DEPLOYING_GROUP,
            DeviceTesterLogMessageType.CLEANING_UP,
            DeviceTesterLogMessageType.CREATING_GREENGRASS_LAMBDAS,
            DeviceTesterLogMessageType.FINISHED_CREATING_GREENGRASS_LAMBDAS,
            DeviceTesterLogMessageType.CREATING_GREENGRASS_GROUP,
            DeviceTesterLogMessageType.FINISHED_DEPLOYING_GROUP,
            DeviceTesterLogMessageType.RESTARTING_GREENGRASS,
            DeviceTesterLogMessageType.RESTARTING_GREENGRASS_SUCCESSFUL,
            DeviceTesterLogMessageType.EMPTY);
    private static final List<DeviceTesterLogMessageType> INFO_MESSAGE_TYPES = List.of(
            DeviceTesterLogMessageType.ALL_TESTS_FINISHED,
            DeviceTesterLogMessageType.REPORT_GENERATED);
    private static final List<DeviceTesterLogMessageType> WARN_MESSAGE_TYPES = List.of(
            DeviceTesterLogMessageType.CLEANING_UP_RESOURCES_FAILED,
            DeviceTesterLogMessageType.FAIL_TO_RESTORE_GREENGRASS);
    private static final List<DeviceTesterLogMessageType> ERROR_MESSAGE_TYPES = List.of(
            DeviceTesterLogMessageType.ERRORS_WHEN_CLEANING_UP_RESOURCES,
            DeviceTesterLogMessageType.UNKNOWN_FAILURE,
            DeviceTesterLogMessageType.TIMED_OUT,
            DeviceTesterLogMessageType.TEST_TIMED_OUT,
            DeviceTesterLogMessageType.COULD_NOT_FIND_GREENGRASS_RELEASE,
            DeviceTesterLogMessageType.XML_SYNTAX_ERROR,
            DeviceTesterLogMessageType.STATUS_CODE_ERROR,
            DeviceTesterLogMessageType.CREDENTIALS_NOT_FOUND,
            DeviceTesterLogMessageType.TEST_EXITED_UNSUCCESSFULLY,
            DeviceTesterLogMessageType.FAIL_TO_REMOVE_GREENGRASS,
            DeviceTesterLogMessageType.COMMAND_ON_REMOTE_HOST_FAILED_TO_START,
            DeviceTesterLogMessageType.FAIL_TO_ADD_REMOTE_FILE_RESOURCE);

    @Override
    public DeviceTesterLogMessageType getLogMessageType(String logMessage) {
        Map<String, String> values = extractValuesFromLogMessage(logMessage);
        String message = values.get(MESSAGE_FIELD_NAME).get();

        // Find the first message type that matches this string
        Optional<DeviceTesterLogMessageType> optionalDeviceTesterLogMessageType = Arrays.stream(DeviceTesterLogMessageType.values())
                .filter(type -> type.matches(message))
                .findFirst();

        if (!optionalDeviceTesterLogMessageType.isPresent()) {
            // No match was found, throw an exception immediately
            throw new RuntimeException(String.format("No match for log message [%s]", logMessage));
        }

        return optionalDeviceTesterLogMessageType.get();
    }

    @Override
    public void log(String logMessage) {
        DeviceTesterLogMessageType deviceTesterLogMessageType = getLogMessageType(logMessage);
        Map<String, String> values = extractValuesFromLogMessage(logMessage);
        String message = values.get(MESSAGE_FIELD_NAME).get();
        Option<String> optionalTestCaseId = getOptionalTestCaseId(values);

        log.debug(logMessage);

        Match(deviceTesterLogMessageType).of(
                // Do nothing with ignored message types
                Case($(IGNORED_MESSAGE_TYPES::contains), type -> type),
                // Info log level messages
                Case($(INFO_MESSAGE_TYPES::contains), type -> infoLog(message)),
                // Warn log level messages
                Case($(WARN_MESSAGE_TYPES::contains), type -> warnLog(message)),
                // Error log level messages
                Case($(ERROR_MESSAGE_TYPES::contains), type -> errorLog(message)),
                // Log messages that we are rewriting
                Case($(DeviceTesterLogMessageType.RUNNING), type -> logStartingTest(optionalTestCaseId)),
                Case($(DeviceTesterLogMessageType.RUNNING_GREENGRASS_ALREADY_INSTALLED), type -> logStartingTest(optionalTestCaseId)),
                Case($(DeviceTesterLogMessageType.FINISHED), type -> logFinishedTest(optionalTestCaseId)),
                Case($(DeviceTesterLogMessageType.PASS), type -> logPassed(optionalTestCaseId)),
                Case($(DeviceTesterLogMessageType.FAIL_WITH_DURATION), type -> logFailureWithDuration(message, optionalTestCaseId)),
                Case($(DeviceTesterLogMessageType.PROVISIONING), type -> logProvisioning(optionalTestCaseId)),
                Case($(DeviceTesterLogMessageType.START), type -> logRunning(optionalTestCaseId)),
                // Always fail if there was no match
                Case($(), type -> {
                    throw new RuntimeException(String.format("No match for log message [%s]", logMessage));
                }));
    }

    private Void infoLog(String string) {
        log.info(string);
        return null;
    }

    private Void warnLog(String string) {
        log.warn(string);
        return null;
    }

    private Void errorLog(String string) {
        log.error(string);
        return null;
    }

    private Void logRunning(Option<String> optionalTestCaseId) {
        log.info(addTestCaseId("Running", optionalTestCaseId));
        return null;
    }

    private Void logProvisioning(Option<String> optionalTestCaseId) {
        log.info(addTestCaseId("Provisioning", optionalTestCaseId));
        return null;
    }

    private Void logFailureWithDuration(String logMessage, Option<String> optionalTestCaseId) {
        Optional<Duration> optionalDuration = extractDurationFromFailureMessage(logMessage);

        String output = addTestCaseId("FAILED", optionalTestCaseId);

        if (optionalDuration.isPresent()) {
            output = output + " [" + optionalDuration.get().toString() + "]";
        }

        log.warn(output);
        log.warn("");

        return null;
    }

    private Void logPassed(Option<String> optionalTestCaseId) {
        log.info(addTestCaseId("PASSED", optionalTestCaseId));
        log.info("");
        return null;
    }

    private Void logFinishedTest(Option<String> optionalTestCaseId) {
        log.info(addTestCaseId("Finished test", optionalTestCaseId));
        return null;
    }

    private Void logStartingTest(Option<String> optionalTestCaseId) {
        log.info(addTestCaseId("Starting test", optionalTestCaseId));
        return null;
    }

    @Override
    public Option<String> getOptionalTestCaseId(Map<String, String> values) {
        return values.get(TEST_CASE_ID);
    }

    private String addTestCaseId(String string, Option<String> optionalTestCaseId) {
        if (optionalTestCaseId.isEmpty()) {
            return string;
        }

        string = StringUtils.rightPad(string, 30);
        string = string + "| " + optionalTestCaseId.get();
        return string;
    }

    private Optional<Duration> extractDurationFromFailureMessage(String logMessage) {
        Pattern pattern = Pattern.compile("\\([0-9]+\\.[0-9]+s\\)");
        Matcher matcher = pattern.matcher(logMessage);

        if (!matcher.find()) {
            return Optional.empty();
        }

        return Try.of(() -> convertDurationStringToOptionalDuration(matcher))
                .getOrElse(Optional.empty());
    }

    private Optional<Duration> convertDurationStringToOptionalDuration(Matcher matcher) {
        String durationString = matcher.group();

        // Removing trailing "s" character and surrounding parenthesis
        durationString = durationString.substring(1, durationString.length() - 2);

        return Optional.of(Duration.ofMillis((long) (Double.valueOf(durationString) * 1000)));
    }

    @Override
    public Map<String, String> extractValuesFromLogMessage(String logMessage) {
        // Find the indexes of the names of the log message values
        Pattern pattern = Pattern.compile(KEY_PATTERN);
        Matcher matcher = pattern.matcher(logMessage);

        // Start with an empty list
        List<Tuple2<Integer, Integer>> startAndEndLocations = List.empty();

        // Loop through all of the matches
        while (matcher.find()) {
            // Store the start and end of the name so it can be extracted later
            startAndEndLocations = startAndEndLocations.append(new Tuple2(matcher.start(), matcher.end()));
        }

        // Add a final entry to the list so we can easily find the end of the last value
        startAndEndLocations = startAndEndLocations.append(new Tuple2(logMessage.length(), 0));

        // Create a list of the locations that's easier to work with (key start, key end, value end)
        List<Tuple3<Integer, Integer, Integer>> finalLocations = List.empty();

        // Map the values in the original list to the final location list
        for (int loop = 0; loop < startAndEndLocations.length() - 1; loop++) {
            Tuple2<Integer, Integer> currentLocation = startAndEndLocations.get(loop);
            Tuple2<Integer, Integer> nextLocation = startAndEndLocations.get(loop + 1);

            int keyStart = currentLocation._1;
            int keyEnd = currentLocation._2;
            int valueEnd = nextLocation._1;

            Tuple3<Integer, Integer, Integer> finalLocation = new Tuple3(keyStart, keyEnd, valueEnd);
            finalLocations = finalLocations.append(finalLocation);
        }

        Map<String, String> values = finalLocations.toStream()
                .map(tuple3 -> tupleToKeyValue(logMessage, tuple3))
                .toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue);

        if (values.get(MESSAGE_FIELD_NAME).isEmpty()) {
            values = values.put(MESSAGE_FIELD_NAME, "");
        }

        // Is there a time value?
        Option<String> optionalTime = values.get(TIME);

        if (optionalTime.isEmpty()) {
            // No, just return everything as is
            return values;
        }

        // Yes, clean up the time a bit
        String time = optionalTime.get();

        // Remove double quotes
        time = time.substring(1, time.length() - 1);

        // Convert to a date and back to a string to get better formatting
        // time = LocalDateTime.parse(time).toString();

        values = values.put(TIME, time);

        return values;
    }

    private AbstractMap.SimpleEntry<String, String> tupleToKeyValue(String logMessage, Tuple3<Integer, Integer, Integer> tuple3) {
        int keyStart = tuple3._1;
        int keyEnd = tuple3._2 - 1;

        int valueStart = tuple3._2;
        int valueEnd = (tuple3._3 == logMessage.length()) ? tuple3._3 : tuple3._3 - 1;

        String key = logMessage.substring(keyStart, keyEnd);
        String value = logMessage.substring(valueStart, valueEnd);

        return new AbstractMap.SimpleEntry<>(key, value);
    }
}
