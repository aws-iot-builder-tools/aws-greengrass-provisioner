package com.awslabs.aws.greengrass.provisioner.implementations.builders;

import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.ExecutableBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ProcessHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BasicExecutableBuilder implements ExecutableBuilder {
    private static final String BUILD_SH = "build.sh";
    private final Logger log = LoggerFactory.getLogger(BasicExecutableBuilder.class);
    @Inject
    ProcessHelper processHelper;
    @Inject
    LoggingHelper loggingHelper;
    @Inject
    IoHelper ioHelper;

    @Inject
    public BasicExecutableBuilder() {
    }

    public void buildExecutableFunctionIfNecessary(FunctionConf functionConf) {
        File buildScript = new File(getBuildScriptName(functionConf));

        if (!buildScript.exists()) {
            log.warn("Builder for executable function was called but no build script [" + BUILD_SH + "] was present");
            return;
        }

        buildExecutable(functionConf);
    }

    @NotNull
    private String getBuildScriptName(FunctionConf functionConf) {
        return String.join("/", functionConf.getBuildDirectory().get().toString(), BUILD_SH);
    }

    private void buildExecutable(FunctionConf functionConf) {
        String buildScriptName = getBuildScriptName(functionConf);

        log.info("Making executable [" + buildScriptName + "]");
        ioHelper.makeExecutable(buildScriptName);

        loggingHelper.logInfoWithName(log, functionConf.getFunctionName().getName(), "Building executable/native function");

        List<String> programAndArguments = new ArrayList<>();
        programAndArguments.add(buildScriptName);

        ProcessBuilder processBuilder = processHelper.getProcessBuilder(programAndArguments);
        processBuilder.directory(functionConf.getBuildDirectory().get().toFile());

        List<String> stdoutStrings = new ArrayList<>();
        List<String> stderrStrings = new ArrayList<>();

        Optional<Integer> exitVal = processHelper.getOutputFromProcess(log, processBuilder, true, Optional.of(stdoutStrings::add), Optional.of(stderrStrings::add));

        if (!exitVal.isPresent() || exitVal.get() != 0) {
            log.error("Something went wrong with while building the executable/native function");

            stderrStrings.forEach(log::warn);

            log.error("To resolve:");
            log.error("1) GGP sometimes must run outside of Docker if the build script requires Docker. Try running GGP outside of Docker.");
            log.error("2) Run " + BUILD_SH + " for the [" + functionConf.getFunctionName().getName() + "] function outside of GGP and determine if it builds properly");

            System.exit(1);
        }
    }

    @Override
    public Optional<String> verifyHandlerExists(FunctionConf functionConf) {
        // Not implemented
        return Optional.empty();
    }
}
