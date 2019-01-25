package com.awslabs.aws.greengrass.provisioner.data.arguments;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.beust.jcommander.Parameter;
import lombok.Getter;

public class TestArguments extends Arguments {
    private final String LONG_TEST_GROUP_OPTION = "--test-group";
    @Getter
    private final String requiredOptionName = LONG_TEST_GROUP_OPTION;
    private final String LONG_USER_OPTION = "--user";
    private final String SHORT_USER_OPTION = "-u";
    private final String LONG_PRIVATE_KEY_PATH_OPTION = "--private-key-path";
    private final String SHORT_PRIVATE_KEY_PATH_OPTION = "-k";
    private final String LONG_DUT_OPTION = "--device-under-test";
    private final String SHORT_DUT_OPTION = "--dut";
    private final String LONG_CLEAN_OPTION = "--clean";
    private final String LONG_GENERATE_CONFIG_OPTION = "--generate-config";
    private final String LONG_DEVICE_TESTER_LOCATION_OPTION = "--device-tester-location";
    private final String SHORT_DEVICE_TESTER_LOCATION_OPTION = "--dtl";
    private final String LONG_OUTPUT_DIRECTORY_OPTION = "--output";
    private final String SHORT_OUTPUT_DIRECTORY_OPTION = "-o";
    @Parameter(names = {LONG_TEST_GROUP_OPTION}, description = "Use Device Tester on a device")
    public boolean testGroup;

    @Parameter(names = {LONG_ARCHITECTURE_OPTION, SHORT_ARCHITECTURE_OPTION}, description = "Architecture (X86_64, ARM32, ARM64)")
    public String architectureString;
    public Architecture architecture;
    @Parameter(names = {LONG_USER_OPTION, SHORT_USER_OPTION}, description = "The username to use for SSH")
    public String user;
    @Parameter(names = {LONG_PRIVATE_KEY_PATH_OPTION, SHORT_PRIVATE_KEY_PATH_OPTION}, description = "The path to the private key for SSH")
    public String privateKeyPath;
    @Parameter(names = {LONG_DUT_OPTION, SHORT_DUT_OPTION}, description = "Hostname or the IP of the device under test")
    public String deviceUnderTest;
    @Parameter(names = {LONG_GROUP_NAME_OPTION, SHORT_GROUP_NAME_OPTION}, description = "The name of the Greengrass group")
    public String groupName;
    @Parameter(names = {LONG_DEVICE_TESTER_LOCATION_OPTION, SHORT_DEVICE_TESTER_LOCATION_OPTION}, description = "The location of the Device Tester directory or ZIP file")
    public String deviceTesterLocation;
    @Parameter(names = {LONG_CLEAN_OPTION}, description = "Cleans the /var/lib/GGQ directory")
    public boolean clean;
    @Parameter(names = {LONG_GENERATE_CONFIG_OPTION}, description = "Generates the config in the /var/lib/GGQ, required for HSI testing")
    public boolean generateConfig;
    @Parameter(names = {LONG_OUTPUT_DIRECTORY_OPTION, SHORT_OUTPUT_DIRECTORY_OPTION}, description = "The directory to place the test results in")
    public String outputDirectory;
    @Parameter(names = "--help", help = true)
    @Getter
    public boolean help;

    @Override
    public boolean isRequiredOptionSet() {
        return testGroup;
    }
}
