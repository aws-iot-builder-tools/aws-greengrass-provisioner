package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.arguments.HsiBootstrapArguments;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.*;
import com.jcraft.jsch.Session;
import io.vavr.control.Try;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetSessionTokenResponse;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class BasicHsiBootstrapHelper implements HsiBootstrapHelper {
    public static final String BOOTSTRAP_COMMON_SH = "bootstrap-common.sh";
    public static final String BOOTSTRAP_VENDOR_PREFIX = "bootstrap-";
    public static final String GREENGRASS_HSI = "greengrass-hsi";
    public static final String BOOTSTRAP_COMMON_SH_RESOURCE_PATH = String.join("/", GREENGRASS_HSI, BOOTSTRAP_COMMON_SH);
    public static final String ARN_AWS_IOT = "arn:aws:iot:";
    public static final String CERT = ":cert/";
    public static final String SUCCESS = "SUCCESS: ";
    public static final String ERROR = "ERROR: ";
    public static final String PKCS_11_URL = "PKCS11_URL";
    private final Logger log = LoggerFactory.getLogger(BasicHsiBootstrapHelper.class);
    @Inject
    HsiBootstrapArgumentHelper hsiBootstrapArgumentHelper;
    @Inject
    IoHelper ioHelper;
    @Inject
    SshHelper sshHelper;
    @Inject
    JavaResourceHelper javaResourceHelper;
    @Inject
    StsClient stsClient;
    @Inject
    AwsHelper awsHelper;

    @Inject
    public BasicHsiBootstrapHelper() {
    }

    @Override
    public void execute(HsiBootstrapArguments hsiBootstrapArguments) {
        log.info("Copying bootstrap script to host via scp...");

        // Get an SSH session to the target
        Session session = sshHelper.getSshSession(hsiBootstrapArguments.targetHost, hsiBootstrapArguments.targetUser);

        final String bootstrapVendorPath = (String.join("", BOOTSTRAP_VENDOR_PREFIX, hsiBootstrapArguments.hsiVendor.name(), ".sh")).toLowerCase();
        final String bootstrapVendorResourcePath = String.join("/", GREENGRASS_HSI, bootstrapVendorPath);

        // Get input streams for the resources
        InputStream bootstrapCommonShStream = javaResourceHelper.getResourceAsStream(BOOTSTRAP_COMMON_SH_RESOURCE_PATH);

        // Get the bootstrap script for this vendor as a string, replace the PKCS11 URL into it
        String bootstrapVendorShString = javaResourceHelper.resourceToString(bootstrapVendorResourcePath);
        bootstrapVendorShString = bootstrapVendorShString.replaceAll(PKCS_11_URL, hsiBootstrapArguments.hsiVendor.getPkcs11Url());

        // Turn it back into an input stream
        InputStream bootstrapVendorShStream = new ByteArrayInputStream(bootstrapVendorShString.getBytes());

        // Copy the files
        Try.run(() -> ioHelper.sendFile(session, bootstrapCommonShStream, BOOTSTRAP_COMMON_SH_RESOURCE_PATH, BOOTSTRAP_COMMON_SH)).get();
        Try.run(() -> ioHelper.sendFile(session, bootstrapVendorShStream, bootstrapVendorResourcePath, bootstrapVendorPath)).get();

        // Make them executable
        makeExecutable(session, bootstrapVendorPath);
        makeExecutable(session, BOOTSTRAP_COMMON_SH);

        String temporaryConfiguration = getTemporaryConfiguration();

        // Run the HSI script on the remote host
        log.info("Running HSI bootstrap script");
        String command = String.join("", "$SHELL -l -c \"", temporaryConfiguration, "./", bootstrapVendorPath, "\"");
        List<String> output = Try.of(() -> ioHelper.runCommand(session, command, Optional.of(log::info))).get();
        log.info("Finished running HSI bootstrap script");

        // Disconnect SSH so GGP can exit
        session.disconnect();

        Optional<String> optionalSuccess = output.stream()
                .filter(string -> string.contains(SUCCESS))
                .filter(string -> string.contains(ARN_AWS_IOT))
                .filter(string -> string.contains(CERT))
                .findFirst();

        if (optionalSuccess.isPresent()) {
            String successString = optionalSuccess.get();
            String arn = successString.substring(successString.indexOf(ARN_AWS_IOT));

            log.info(String.join("", "HSI successfully bootstrapped. ARN for the certificate is: ", arn));

            return;
        }

        Optional<String> optionalError = output.stream()
                .filter(string -> string.contains(ERROR))
                .findFirst();

        if (optionalError.isPresent()) {
            log.error(String.join("", "HSI bootstrap failed for the following reason [", optionalError.get(), "]"));
        } else {
            log.error("HSI bootstrap failed for an unknown reason");
        }
    }

    @NotNull
    public String getTemporaryConfiguration() {
        GetSessionTokenResponse getSessionTokenResponse = stsClient.getSessionToken();
        Credentials credentials = getSessionTokenResponse.credentials();

        Region currentRegion = awsHelper.getCurrentRegion();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("AWS_ACCESS_KEY_ID=");
        stringBuilder.append(credentials.accessKeyId());
        stringBuilder.append(" ");
        stringBuilder.append("AWS_SECRET_ACCESS_KEY=");
        stringBuilder.append(credentials.secretAccessKey());
        stringBuilder.append(" ");
        stringBuilder.append("AWS_SESSION_TOKEN=");
        stringBuilder.append(credentials.sessionToken());
        stringBuilder.append(" ");
        stringBuilder.append("AWS_DEFAULT_REGION=");
        stringBuilder.append(currentRegion.id());
        stringBuilder.append(" ");

        return stringBuilder.toString();
    }

    public void makeExecutable(Session session, String filename) {
        Try.of(() -> ioHelper.runCommand(session, String.join(" ", "chmod", "+x", String.join("", "./", filename)))).get();
    }

    @Override
    public ArgumentHelper<HsiBootstrapArguments> getArgumentHelper() {
        return hsiBootstrapArgumentHelper;
    }

    @Override
    public HsiBootstrapArguments getArguments() {
        return new HsiBootstrapArguments();
    }
}
