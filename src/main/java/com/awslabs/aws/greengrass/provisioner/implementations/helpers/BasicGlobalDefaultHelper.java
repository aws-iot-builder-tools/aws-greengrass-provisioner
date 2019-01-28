package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GlobalDefaultHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.jcraft.jsch.JSchException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vavr.control.Try;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BasicGlobalDefaultHelper implements GlobalDefaultHelper {
    public static final String USER_HOME = "user.home";
    public static final String GLOBAL_DEFAULTS_DIRECTORY = ".ggprovisioner";

    @Override
    public Optional<Config> getGlobalDefaults(String filename) {
        Optional<String> homeDirectory = getHomeDirectory();

        if (!homeDirectory.isPresent()) {
            return Optional.empty();
        }

        File file = new File(String.join("/", homeDirectory.get(), GLOBAL_DEFAULTS_DIRECTORY, filename));

        if (!file.exists()) {
            return Optional.empty();
        }

        Config defaults = ConfigFactory.parseFile(file);

        return Optional.of(defaults);
    }

    @Override
    public Optional<String> getHomeDirectory() {
        return Optional.ofNullable(System.getProperty(USER_HOME));
    }
}
