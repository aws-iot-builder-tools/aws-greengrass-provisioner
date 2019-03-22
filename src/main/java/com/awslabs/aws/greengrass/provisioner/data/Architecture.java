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
    ARM32("greengrass-linux-armv7l-1.8.0.tar.gz", "6df4aa6400f1c20269ba76791bfa78375706fe6b"),
    X86_64("greengrass-linux-x86-64-1.8.0.tar.gz", "c4a5364d37aa35140c7d237547e5ab39bdc28154"),
    ARM64("greengrass-linux-aarch64-1.8.0.tar.gz", "0d240d6f569075093232e3a4feb62fd8f59a7a6f");

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
