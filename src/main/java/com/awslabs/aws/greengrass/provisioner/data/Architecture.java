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
    ARM32("greengrass-linux-armv7l-1.9.1.tar.gz", "dd61136e491bfa77503ede9ed3d59538c9c30add"),
    X86_64("greengrass-linux-x86-64-1.9.1.tar.gz", "1a1483daa4b1f2062fba47feff1ecf59cddbc1ec"),
    ARM64("greengrass-linux-aarch64-1.9.1.tar.gz", "c949da81abbf9f2be43758f4dcc529732b38f691");

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
