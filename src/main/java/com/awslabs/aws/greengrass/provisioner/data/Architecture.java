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
    ARM32("greengrass-linux-armv7l-1.7.0.tar.gz", "8ad40c4b982f222f48945829b702cd1a9835bc4d"),
    X86_64("greengrass-linux-x86-64-1.7.0.tar.gz", "3ead1528fa23320418d6ffb25ca4f6feed70779e"),
    ARM64("greengrass-linux-aarch64-1.7.0.tar.gz", "30ac94957f9adbd628ce1ae2aa53fe15b1f29d66");

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
