package com.awslabs.aws.greengrass.provisioner.data;

import io.vavr.control.Try;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.io.Resources.getResource;

public enum Architecture {
    ARM32(null), // Legacy
    ARM64(null), // Legacy
    ARMV8("greengrass-linux-aarch64-VERSION.tar.gz"),
    ARMV8_OPENWRT("greengrass-openwrt-aarch64-VERSION.tar.gz"),
    ARMV7L_RASPBIAN("greengrass-linux-armv7l-VERSION.tar.gz"),
    ARMV7L_OPENWRT("greengrass-openwrt-armv7l-VERSION.tar.gz"),
    ARMV6L_RASPBIAN("greengrass-linux-armv6l-VERSION.tar.gz"),
    X86_64("greengrass-linux-x86-64-VERSION.tar.gz");

    private final String currentVersion = "1.10.2";
    private final String filename;

    Architecture(String filename) {
        this.filename = Optional.ofNullable(filename)
                .map(value -> value.replace("VERSION", currentVersion))
                .orElse(null);
    }

    public static String getList() {
        return Arrays.stream(Architecture.values())
                .map(Architecture::name)
                .collect(Collectors.joining(", "));
    }

    public String getWebUrl() {
        return String.join("/", "https://d1onfpft10uf5o.cloudfront.net/greengrass-core/downloads", currentVersion, filename);
    }

    public String getFilename() {
        return filename;
    }

    public String getDIST() {
        return "dist";
    }

    public String getResourcePath() {
        return String.join("/", getDIST(), getFilename());
    }

    public Optional<URL> getResourceUrl() {
        String resourcePath = getResourcePath();

        File resource = new File(resourcePath);

        if (!resource.exists()) {
            return Optional.ofNullable(getResource(resourcePath));
        }

        return Try.of(() -> Optional.of(resource.toURI().toURL())).get();
    }
}
