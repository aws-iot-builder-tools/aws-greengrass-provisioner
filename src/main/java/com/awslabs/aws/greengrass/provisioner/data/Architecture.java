package com.awslabs.aws.greengrass.provisioner.data;

import io.vavr.control.Try;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.io.Resources.getResource;

public enum Architecture {
    ARM32("greengrass-linux-armv7l-1.9.1.tar.gz"),
    X86_64("greengrass-linux-x86-64-1.9.1.tar.gz"),
    ARM64("greengrass-linux-aarch64-1.9.1.tar.gz");

    private final String filename;

    Architecture(String filename) {
        this.filename = filename;
    }

    public static String getList() {
        return Arrays.stream(Architecture.values())
                .map(Architecture::name)
                .collect(Collectors.joining(", "));
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
