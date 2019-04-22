package com.awslabs.aws.greengrass.provisioner.data;

import io.vavr.control.Try;
import lombok.Getter;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.io.Resources.getResource;

public enum Architecture {
    ARM32("greengrass-linux-armv7l-1.8.1.tar.gz", "40e6c95b7783dc8cebb4c05d51658f46d3cc4146"),
    X86_64("greengrass-linux-x86-64-1.8.1.tar.gz", "28013ce211350da2ca41435041b383f931a8c2ac"),
    ARM64("greengrass-linux-aarch64-1.8.1.tar.gz", "0f7cd7ab36fe24ac0e87c3906b8c6c82049cfd3e");

    @Getter
    private final String DIST = "dist";

    @Getter
    private final String filename;

    @Getter
    private final String hash;

    @Getter(lazy = true)
    private final String resourcePath = innerGetResourcePath();

    @Getter(lazy = true)
    private final Optional<URL> resourceUrl = innerGetResourceUrl();

    Architecture(String filename, String hash) {
        this.filename = filename;
        this.hash = hash;
    }

    public static String getList() {
        return Arrays.stream(Architecture.values())
                .map(Architecture::name)
                .collect(Collectors.joining(", "));
    }

    private String innerGetResourcePath() {
        return String.join("/", DIST, getFilename());
    }

    private Optional<URL> innerGetResourceUrl() {
        String resourcePath = getResourcePath();

        File resource = new File(resourcePath);

        if (!resource.exists()) {
            return Optional.ofNullable(getResource(resourcePath));
        }

        return Try.of(() -> Optional.of(resource.toURI().toURL())).get();
    }
}
