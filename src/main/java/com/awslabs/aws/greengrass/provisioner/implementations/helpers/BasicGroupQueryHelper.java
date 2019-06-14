package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.QueryArguments;
import com.awslabs.aws.greengrass.provisioner.implementations.clientproviders.CloudWatchLogsClientProvider;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.control.Try;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import software.amazon.awssdk.services.greengrass.model.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class BasicGroupQueryHelper implements GroupQueryHelper {
    private final Logger log = LoggerFactory.getLogger(BasicGroupQueryHelper.class);
    private final List<String> greengrassTopLevelLogNames = Arrays.asList("/aws/greengrass/GreengrassSystem/GGCloudSpooler",
            "/aws/greengrass/GreengrassSystem/GGConnManager",
            "/aws/greengrass/GreengrassSystem/GGDeviceCertificateManager",
            "/aws/greengrass/GreengrassSystem/GGIPDetector",
            "/aws/greengrass/GreengrassSystem/GGSecretManager",
            "/aws/greengrass/GreengrassSystem/GGShadowService",
            "/aws/greengrass/GreengrassSystem/GGShadowSyncManager",
            "/aws/greengrass/GreengrassSystem/GGTES",
            "/aws/greengrass/GreengrassSystem/runtime");
    @Inject
    GreengrassHelper greengrassHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    QueryArgumentHelper queryArgumentHelper;
    @Inject
    CloudWatchLogsClientProvider cloudWatchLogsClientProvider;
    @Inject
    GGVariables ggVariables;

    @Inject
    public BasicGroupQueryHelper() {
    }

    @Override
    public Void execute(QueryArguments queryArguments) {
        if (!queryArguments.getGroupCa &&
                !queryArguments.listSubscriptions &&
                !queryArguments.listFunctions &&
                !queryArguments.listDevices &&
                !queryArguments.downloadLogs &&
                !queryArguments.watchLogs) {
            throw new RuntimeException("No query specified");
        }

        Optional<GroupInformation> optionalGroupInformation = greengrassHelper.getGroupInformation(queryArguments.groupName);

        if (!optionalGroupInformation.isPresent()) {
            throw new RuntimeException("Group [" + queryArguments.groupName + "] not found");
        }

        GroupInformation groupInformation = optionalGroupInformation.get();

        if (queryArguments.getGroupCa) {
            GetGroupCertificateAuthorityResponse getGroupCertificateAuthorityResponse = greengrassHelper.getGroupCa(groupInformation);

            if (getGroupCertificateAuthorityResponse == null) {
                throw new RuntimeException("Couldn't get the group CA");
            }

            String pem = getGroupCertificateAuthorityResponse.pemEncodedCertificate();
            log.info("Group CA for group [" + queryArguments.groupName + "]\n" + pem);

            String outputFilename = "build/" + queryArguments.groupName + "_Core_CA.pem";

            writeToFile(queryArguments, pem, outputFilename);

            return null;
        }

        if (queryArguments.listSubscriptions) {
            List<Subscription> subscriptions = greengrassHelper.getSubscriptions(groupInformation);

            log.info("Subscriptions:");
            String output = jsonHelper.toJson(subscriptions);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_subscription_table.json";

            writeToFile(queryArguments, output, outputFilename);

            return null;
        }

        if (queryArguments.listFunctions) {
            List<Function> functions = greengrassHelper.getFunctions(groupInformation);

            log.info("Functions:");
            String output = jsonHelper.toJson(functions);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_function_table.json";

            writeToFile(queryArguments, output, outputFilename);

            return null;
        }

        if (queryArguments.listDevices) {
            List<Device> devices = greengrassHelper.getDevices(groupInformation);

            log.info("Devices:");
            String output = jsonHelper.toJson(devices);
            log.info(output);

            String outputFilename = "build/" + queryArguments.groupName + "_device_table.json";

            writeToFile(queryArguments, output, outputFilename);

            return null;
        }

        if (queryArguments.downloadLogs) {
            List<Tuple3<LogGroup, LogStream, GetLogEventsResponse>> logEvents = getLatestLogEventsForGroup(queryArguments, groupInformation);

            List<Tuple3<LogGroup, LogStream, String>> logs = logEvents.stream()
                    .map(this::formatLogEvents)
                    .collect(Collectors.toList());

            File directory = cleanAndCreateDirectory(queryArguments.groupName);

            logs.forEach(events -> saveLogEvents(directory, events));

            if (!ioHelper.isRunningInDocker()) {
                log.info("Logs written to [" + directory.getPath() + "]");
            } else {
                // Remove the leading slash
                log.info("Logs copied to host in [" + directory.getPath().substring(1) + "]");
            }

            return null;
        }

        if (queryArguments.watchLogs) {
            List<Tuple3<LogGroup, LogStream, GetLogEventsResponse>> logEvents = getLatestLogEventsForGroup(queryArguments, groupInformation);

            List<Tuple3<LogGroup, LogStream, String>> logGroupStreamAndForwardTokens = logEvents.stream()
                    .map(this::getNextForwardTokenForEvents)
                    .collect(Collectors.toList());

            do {
                // Get the new events using the forward tokens
                List<Tuple3<LogGroup, LogStream, GetLogEventsResponse>> newEvents = logGroupStreamAndForwardTokens.stream()
                        .map(this::getLogEvents)
                        .collect(Collectors.toList());

                // Print the new events
                newEvents.forEach(this::printLogEvents);

                // Get the next set of forward tokens
                logGroupStreamAndForwardTokens = newEvents.stream()
                        .map(this::getNextForwardTokenForEvents)
                        .collect(Collectors.toList());

                // Sleep so we don't hit the CloudWatch Logs APIs too much
                ioHelper.sleep(1000);
            } while (true);
        }

        throw new RuntimeException("This should never happen.  This is a bug.");
    }

    @NotNull
    private List<Tuple3<LogGroup, LogStream, GetLogEventsResponse>> getLatestLogEventsForGroup(QueryArguments queryArguments, GroupInformation groupInformation) {
        List<LogGroup> allLogGroups = getAllLogGroupsForGreengrassGroup(groupInformation);

        List<Tuple2<LogGroup, LogStream>> allLogStreams = getAllLogStreamsForGreengrassGroup(queryArguments, allLogGroups);

        return getLogEventsForLogStreams(allLogStreams);
    }

    private void printLogEvents(Tuple3<LogGroup, LogStream, GetLogEventsResponse> logGroupStreamAndEvents) {
        List<OutputLogEvent> outputLogEvents = logGroupStreamAndEvents._3.events();

        if (outputLogEvents.size() == 0) {
            // Nothing to do
            return;
        }

        LogGroup logGroup = logGroupStreamAndEvents._1;
        String trimmedLogStreamName = logGroup.logGroupName().replaceAll("^.*/([^/].*)$", "$1");

        String header = trimmedLogStreamName + " - ";

        outputLogEvents.forEach(event -> printLogEvent(header, event.message()));
    }

    private void printLogEvent(String header, String message) {
        System.out.print(header + message);
    }

    private Tuple3<LogGroup, LogStream, String> getNextForwardTokenForEvents(Tuple3<LogGroup, LogStream, GetLogEventsResponse> logGroupStreamAndEvents) {
        return Tuple.of(logGroupStreamAndEvents._1, logGroupStreamAndEvents._2, logGroupStreamAndEvents._3.nextForwardToken());
    }

    @NotNull
    private List<Tuple3<LogGroup, LogStream, GetLogEventsResponse>> getLogEventsForLogStreams(List<Tuple2<LogGroup, LogStream>> allLogStreams) {
        return allLogStreams.stream()
                .map(this::getLogEvents)
                .collect(Collectors.toList());
    }

    @NotNull
    private List<Tuple2<LogGroup, LogStream>> getAllLogStreamsForGreengrassGroup(QueryArguments queryArguments, List<LogGroup> allLogGroups) {
        String topLevelCloudWatchLogsGroupRegex = getTopLevelCloudWatchLogsGroupRegex(queryArguments.groupName);

        return allLogGroups.stream()
                .map(logGroup -> getLatestLogStreamForLogGroup(logGroup, topLevelCloudWatchLogsGroupRegex))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @NotNull
    private List<LogGroup> getAllLogGroupsForGreengrassGroup(GroupInformation groupInformation) {
        List<LogGroup> greengrassTopLevelLogGroups = greengrassTopLevelLogNames.stream()
                .map(this::findLogGroupByName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        List<Function> functions = greengrassHelper.getFunctions(groupInformation);

        List<LogGroup> functionLogGroups = functions.stream()
                // Remove all internal functions (no region, no account number) since they won't have logs
                .filter(function -> !function.functionArn().contains(":::"))
                // Convert function names into CloudWatch Logs log group format
                .map(this::convertFunctionToCloudWatchLogGroupName)
                // Get the LogGroup object for each
                .map(this::findLogGroupByName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        List<LogGroup> allLogGroups = new ArrayList<>();
        allLogGroups.addAll(greengrassTopLevelLogGroups);
        allLogGroups.addAll(functionLogGroups);

        return allLogGroups;
    }

    private String convertFunctionToCloudWatchLogGroupName(Function function) {
        String name = function.functionArn();
        // Remove the leading "arn" string
        name = name.replaceFirst("arn", "");
        // Replace all colons with forward slashes
        name = name.replaceAll(":", "/");
        // Replace "/lambda" with "/greengrass/Lambda"
        name = name.replaceFirst("/lambda", "/greengrass/Lambda");
        // Remove the "/function" string
        name = name.replaceFirst("/function", "");
        // Remove the alias (the text after the last slash)
        name = name.replaceFirst("/[^/]*$", "");

        return name;
    }

    private void saveLogEvents(File directory, Tuple3<LogGroup, LogStream, String> logEvents) {
        Path directoryPath = directory.toPath();

        String[] fileNameParts = logEvents._1.logGroupName().split("/");
        String fileName = String.join(".", fileNameParts[fileNameParts.length - 1], "log");

        ioHelper.writeFile(directoryPath.resolve(fileName).toFile(), logEvents._3.getBytes());
    }

    @NotNull
    private File cleanAndCreateDirectory(String groupName) {
        String directoryName = String.join("/", "logs", groupName);

        if (ioHelper.isRunningInDocker()) {
            // Logs are written into the root in Docker so they show up in the host
            directoryName = "/" + directoryName;
        }

        File directory = new File(directoryName);

        if (directory.exists()) {
            log.warn("Directory for logs [" + directoryName + "] already exists. Removing old logs.");
            Try.of(() -> deleteDirectory(directory))
                    .recover(IOException.class, this::throwFailedToDeleteDirectoryException)
                    .get();
        }

        directory.mkdirs();

        return directory;
    }

    private Void throwFailedToDeleteDirectoryException(IOException ioException) {
        throw new RuntimeException("Failed to delete previous log directory, can not continue");
    }

    private Void deleteDirectory(File directory) throws IOException {
        FileUtils.deleteDirectory(directory);

        return null;
    }

    private Tuple3<LogGroup, LogStream, String> formatLogEvents(Tuple3<LogGroup, LogStream, GetLogEventsResponse> logEvents) {
        LogGroup logGroup = logEvents._1;
        LogStream logStream = logEvents._2;
        GetLogEventsResponse logEventsResponse = logEvents._3;

        StringBuilder stringBuilder = new StringBuilder();

        logEventsResponse.events().forEach(event -> appendOutputLogEvent(stringBuilder, event));

        return Tuple.of(logGroup, logStream, stringBuilder.toString());
    }

    private void appendOutputLogEvent(StringBuilder stringBuilder, OutputLogEvent event) {
        stringBuilder.append(event.message());
    }

    @NotNull
    private Optional<Tuple2<LogGroup, LogStream>> getLatestLogStreamForLogGroup(LogGroup logGroup, String regex) {
        Optional<LogStream> latestLogStreamForLogGroup = findLatestLogStream(logGroup.logGroupName(), regex);

        return latestLogStreamForLogGroup.map(logStream -> Tuple.of(logGroup, logStream));
    }

    @Override
    public ArgumentHelper<QueryArguments> getArgumentHelper() {
        return queryArgumentHelper;
    }

    @Override
    public QueryArguments getArguments() {
        return new QueryArguments();
    }

    private String getTopLevelCloudWatchLogsGroupRegex(String groupName) {
        String coreThingName = ggVariables.getCoreThingName(groupName);

        return String.join("", ".*/", coreThingName, "$");
    }

    private void writeToFile(QueryArguments queryArguments, String output, String outputFilename) {
        if (queryArguments.writeToFile) {
            ioHelper.writeFile(outputFilename, output.getBytes());
            log.info("This data was also written to [" + outputFilename + "]");
        }
    }

    // Adapted from AWS SDK v2 integration tests
    private Optional<LogGroup> findLogGroupByName(String logGroupName) {
        String nextToken = null;

        CloudWatchLogsClient cloudWatchLogsClient = cloudWatchLogsClientProvider.get();

        do {
            DescribeLogGroupsResponse result = cloudWatchLogsClient
                    .describeLogGroups(DescribeLogGroupsRequest.builder().nextToken(nextToken).build());

            Optional<LogGroup> optionalLogGroup = result.logGroups().stream()
                    .filter(group -> group.logGroupName().equals(logGroupName))
                    .findFirst();

            if (optionalLogGroup.isPresent()) {
                return optionalLogGroup;
            }

            nextToken = result.nextToken();
        } while (nextToken != null);

        return Optional.empty();
    }

    private Optional<LogStream> findLatestLogStream(final String logGroupName,
                                                    final String logStreamRegex) {
        String nextToken = null;

        CloudWatchLogsClient cloudWatchLogsClient = cloudWatchLogsClientProvider.get();

        List<LogStream> logStreams = new ArrayList<>();

        do {
            DescribeLogStreamsResponse result = cloudWatchLogsClient
                    .describeLogStreams(DescribeLogStreamsRequest.builder()
                            .logGroupName(logGroupName)
                            .nextToken(nextToken)
                            .build());

            logStreams.addAll(result.logStreams().stream()
                    .filter(stream -> stream.logStreamName().matches(logStreamRegex))
                    .collect(Collectors.toList()));

            nextToken = result.nextToken();
        } while (nextToken != null);

        // Get the most recent log stream
        return logStreams.stream().max(Comparator.comparingLong(LogStream::creationTime));
    }

    private Tuple3<LogGroup, LogStream, GetLogEventsResponse> getLogEvents(Tuple2<LogGroup, LogStream> logGroupAndStream) {
        return getLogEvents(logGroupAndStream._1, logGroupAndStream._2);
    }

    private Tuple3<LogGroup, LogStream, GetLogEventsResponse> getLogEvents(LogGroup logGroup, LogStream logStream) {
        CloudWatchLogsClient cloudWatchLogsClient = cloudWatchLogsClientProvider.get();

        GetLogEventsRequest getLogEventsRequest = GetLogEventsRequest.builder()
                .logGroupName(logGroup.logGroupName())
                .logStreamName(logStream.logStreamName())
                .build();

        return Tuple.of(logGroup, logStream, cloudWatchLogsClient.getLogEvents(getLogEventsRequest));
    }

    private Tuple3<LogGroup, LogStream, GetLogEventsResponse> getLogEvents(Tuple3<LogGroup, LogStream, String> logGroupStreamAndForwardToken) {
        return getLogEvents(logGroupStreamAndForwardToken._1, logGroupStreamAndForwardToken._2, logGroupStreamAndForwardToken._3);
    }

    private Tuple3<LogGroup, LogStream, GetLogEventsResponse> getLogEvents(LogGroup logGroup, LogStream logStream, String forwardToken) {
        CloudWatchLogsClient cloudWatchLogsClient = cloudWatchLogsClientProvider.get();

        GetLogEventsRequest getLogEventsRequest = GetLogEventsRequest.builder()
                .logGroupName(logGroup.logGroupName())
                .logStreamName(logStream.logStreamName())
                .nextToken(forwardToken)
                .build();

        return Tuple.of(logGroup, logStream, cloudWatchLogsClient.getLogEvents(getLogEventsRequest));
    }
}
