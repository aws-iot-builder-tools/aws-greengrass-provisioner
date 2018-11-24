package com.awslabs.aws.greengrass.provisioner.data;

import lombok.Getter;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.amazonaws.util.ClassLoaderHelper.getResource;

public enum Architecture {
    ARM32("greengrass-linux-armv7l-1.6.0.tar.gz", "735bae7e87dff157ef7122028fa89c8dc7138e81"),
    X86_64("greengrass-linux-x86-64-1.6.0.tar.gz", "ed7cd42b2559f141da9dc16342b658ce908f7348"),
    ARM64("greengrass-linux-aarch64-1.6.0.tar.gz", "2112f37931cf5b35c24ac0b900fe212057a3c02c"),
    UBUNTU_X86("greengrass-ubuntu-x86-64-1.6.0.tar.gz", "f26a74219d37659683490f416781cbfd964db425");

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
            if (!resourcePath.startsWith("/")) {
                // All resources inside a JAR must be absolute
                resourcePath = "/" + resourcePath;
            }

            return Optional.ofNullable(getResource(resourcePath));
        }

        try {
            return Optional.of(resource.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
