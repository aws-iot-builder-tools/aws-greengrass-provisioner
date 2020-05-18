import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.empty;

public class SoftwareDownloadTest {
    private final Logger log = LoggerFactory.getLogger(SoftwareDownloadTest.class);
    private List<Architecture> architectureList;
    private List<String> softwareUrls;
    private List<Architecture> nonNullArchitectures;

    @Before
    public void setup() {
        architectureList = Arrays.asList(Architecture.values());
        nonNullArchitectures = architectureList.stream()
                .filter(architecture -> architecture.getFilename() != null)
                .collect(Collectors.toList());

        softwareUrls = nonNullArchitectures.stream()
                .map(Architecture::getWebUrl)
                .collect(Collectors.toList());
    }

    @Test
    public void softwareUrlsShouldNotBeEmpty() {
        MatcherAssert.assertThat("Software URLs are empty, this means the Greengrass software list is not configured properly", softwareUrls, is(not(empty())));
    }

    @Test
    public void softwareShouldNotReturn404Errors() {
        List<String> failedUrls = softwareUrls.stream()
                .filter(url -> getResponseCode(url) != 200)
                .collect(Collectors.toList());

        if (failedUrls.size() != 0) {
            failedUrls.forEach(url -> log.error(String.join("", "URL [", url, "] failed to download")));
            throw new RuntimeException("At least one Greengrass URL failed to download.");
        }
    }

    @Test
    public void softwareShouldBeAvailableAsResources() {
        List<File> resourceFiles = nonNullArchitectures.stream()
                .map(Architecture::getResourcePath)
                .map(value -> String.join("/", "build", value))
                .map(File::new)
                .collect(Collectors.toList());

        MatcherAssert.assertThat("Greengrass distribution file list is empty, this means the Greengrass software list is not configured properly", resourceFiles, is(not(empty())));

        List<File> missingFiles = resourceFiles.stream()
                .filter(file -> !file.exists())
                .collect(Collectors.toList());

        missingFiles.forEach(file -> log.error(String.join("", "Greengrass distribution file [", file.getName(), "] is missing")));

        MatcherAssert.assertThat("Some Greengrass distributions are missing, this means the Greengrass software list is not configured properly", missingFiles, is(empty()));
    }

    private int getResponseCode(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.connect();
            return httpURLConnection.getResponseCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
