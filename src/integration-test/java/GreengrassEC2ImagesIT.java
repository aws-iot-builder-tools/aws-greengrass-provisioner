import com.awslabs.aws.greengrass.provisioner.AwsGreengrassProvisioner;
import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.data.EC2LinuxVersion;
import com.awslabs.aws.greengrass.provisioner.implementations.helpers.BasicDeploymentHelper;
import io.vavr.Tuple3;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.services.ec2.model.Image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;

public class GreengrassEC2ImagesIT {
    private BasicDeploymentHelper basicDeploymentHelper;

    @Before
    public void setup() {
        basicDeploymentHelper = AwsGreengrassProvisioner.getInjector().getInstance(BasicDeploymentHelper.class);
    }

    @Test
    public void shouldFindAccountIdsForAllEc2LinuxVersions() {
        List<EC2LinuxVersion> ec2LinuxVersions = getValidEc2LinuxVersions();

        // Optional.get() will throw an exception if any aren't present
        ec2LinuxVersions.stream()
                .map(basicDeploymentHelper::getAccountId)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @NotNull
    private List<EC2LinuxVersion> getValidEc2LinuxVersions() {
        return Arrays.asList(EC2LinuxVersion.values());
    }

    @Test
    public void shouldFindInstanceTypeForAllArchitectures() {
        List<Architecture> architectures = getValidArchitectures();

        // Optional.get() will throw an exception if any aren't present
        architectures.stream()
                .map(basicDeploymentHelper::getInstanceType)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @NotNull
    private List<Architecture> getValidArchitectures() {
        List<Architecture> architectures = new ArrayList<>(Arrays.asList(Architecture.values()));

        // There is no ARM32 support on EC2
        architectures.remove(Architecture.ARM32);
        architectures.remove(Architecture.ARMV6L_RASPBIAN);

        // Do not use ARM64 legacy value
        architectures.remove(Architecture.ARM64);

        // Remove the OpenWRT values
        architectures.remove(Architecture.ARMV8_OPENWRT);
        architectures.remove(Architecture.ARMV7L_OPENWRT);

        // Remove the Raspbian values
        architectures.remove(Architecture.ARMV6L_RASPBIAN);
        architectures.remove(Architecture.ARMV7L_RASPBIAN);

        return architectures;
    }

    @Test
    public void shouldFindNameFiltersForAllValidCombinations() {
        // Optional.get() will throw an exception if any aren't present
        getAllNameFilters();
    }

    @Test
    public void shouldFindImagesForAllNameFiltersAndAccountIds() {
        @NotNull List<Tuple3<EC2LinuxVersion, Architecture, String>> validCombinations = getValidCombinations();

        // Optional.get() will throw an exception if any aren't present
        List<Image> images = validCombinations.stream()
                .map(tuple -> basicDeploymentHelper.getImage(tuple._3, basicDeploymentHelper.getAccountId(tuple._1).get()))
                .map(Optional::get)
                .collect(Collectors.toList());

        List<Image> distinctImages = images.stream().distinct().collect(Collectors.toList());

        // Make sure all of the images are distinct
        Assert.assertThat(images.size(), is(distinctImages.size()));
    }

    private List<String> getAllNameFilters() {
        @NotNull List<Tuple3<EC2LinuxVersion, Architecture, String>> validCombinations = getValidCombinations();

        // Optional.get() will throw an exception if any aren't present
        return validCombinations.stream()
                .map(tuple -> basicDeploymentHelper.getNameFilter(tuple._2, tuple._1))
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @NotNull
    private List<Tuple3<EC2LinuxVersion, Architecture, String>> getValidCombinations() {
        List<EC2LinuxVersion> ec2LinuxVersions = getValidEc2LinuxVersions();
        List<Architecture> architectures = getValidArchitectures();

        List<Tuple3<EC2LinuxVersion, Architecture, String>> validCombinations = new ArrayList<>();

        for (EC2LinuxVersion ec2LinuxVersion : ec2LinuxVersions) {
            for (Architecture architecture : architectures) {
                String nameFilter = basicDeploymentHelper.getNameFilter(architecture, ec2LinuxVersion).get();
                validCombinations.add(new Tuple3<>(ec2LinuxVersion, architecture, nameFilter));
            }
        }

        return validCombinations;
    }
}