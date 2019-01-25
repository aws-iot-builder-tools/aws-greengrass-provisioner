package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IotHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ResourceHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ScriptHelper;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;


public class BasicScriptHelper implements ScriptHelper {
    public static final String GGD_PIP_DEPENDENCIES = "GGD_PIP_DEPENDENCIES";
    public static final String LIB_SYSTEMD_SYSTEM_PATH = "/lib/systemd/system";
    private final String shellPath = "shell/";
    @Getter
    private final String installScriptName = "install.sh";
    private final String installTemplatePath = shellPath + installScriptName + ".in";
    @Getter
    private final String startScriptName = "start.sh";
    private final String startTemplatePath = shellPath + startScriptName + ".in";
    @Getter
    private final String stopScriptName = "stop.sh";
    private final String stopTemplatePath = shellPath + stopScriptName + ".in";
    @Getter
    private final String cleanScriptName = "clean.sh";
    private final String cleanTemplatePath = shellPath + cleanScriptName + ".in";
    private final String ggScriptTemplatePath = shellPath + "template.sh.in";
    @Getter
    private final String monitorScriptName = "monitor.sh";
    private final String monitorTemplatePath = shellPath + monitorScriptName + ".in";
    @Getter
    private final String systemdScriptName = "greengrass.service";
    private final String systemdTemplatePath = shellPath + systemdScriptName + ".in";
    @Inject
    GGConstants ggConstants;
    @Inject
    IotHelper iotHelper;
    @Inject
    ResourceHelper resourceHelper;

    @Inject
    public BasicScriptHelper() {
    }

    @Override
    public String generateInstallScript(Architecture architecture) {
        return innerGenerateRunScriptWithArchitecture(installTemplatePath, Optional.ofNullable(architecture));
    }

    @Override
    public String generateStartScript(Architecture architecture) {
        return innerGenerateRunScriptWithArchitecture(startTemplatePath, Optional.ofNullable(architecture));
    }

    @Override
    public String generateStopScript(Architecture architecture) {
        return innerGenerateRunScriptWithArchitecture(stopTemplatePath, Optional.ofNullable(architecture));
    }

    @Override
    public String generateCleanScript(Architecture architecture, String ggShScriptName) {
        return innerGenerateRunScriptWithArchitecture(cleanTemplatePath, Optional.ofNullable(architecture), Optional.ofNullable(ggShScriptName));
    }

    @Override
    public String generateGgScript(Set<String> ggdPipDependencies) {
        return innerGenerateRunScriptWithGgdPipDependencies(ggScriptTemplatePath, Optional.ofNullable(ggdPipDependencies));
    }

    @Override
    public String generateMonitorScript(Architecture architecture) {
        return innerGenerateRunScriptWithArchitecture(monitorTemplatePath, Optional.ofNullable(architecture));
    }

    @Override
    public String generateSystemdScript() {
        return innerGenerateRunScript(systemdTemplatePath, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public String generateRunScript(Optional<Architecture> architecture, String scriptName, String thingName) {
        return innerGenerateRunScript(shellPath + "run-python.sh.in", architecture, Optional.of(scriptName), Optional.of(thingName), Optional.empty());
    }

    private String innerGenerateRunScriptWithArchitecture(String path, Optional<Architecture> architecture) {
        return innerGenerateRunScript(path, architecture, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private String innerGenerateRunScriptWithArchitecture(String path, Optional<Architecture> architecture, Optional<String> scriptName) {
        return innerGenerateRunScript(path, architecture, scriptName, Optional.empty(), Optional.empty());
    }

    private String innerGenerateRunScriptWithGgdPipDependencies(String path, Optional<Set<String>> ggdPipDependencies) {
        return innerGenerateRunScript(path, Optional.empty(), Optional.empty(), Optional.empty(), ggdPipDependencies);
    }

    private void addScriptName(ImmutableMap.Builder<String, String> variables, Optional<String> scriptName) {
        scriptName.ifPresent(name -> putScriptNameAndGgSh(variables, name));
    }

    private void putScriptNameAndGgSh(ImmutableMap.Builder<String, String> variables, String name) {
        variables.put("SCRIPT_NAME", name);
        variables.put("GG_SH", name);
    }

    private void addThingNameVariables(ImmutableMap.Builder<String, String> variables, Optional<String> thingName) {
        thingName.ifPresent(name -> putThingNameCertAndPrivateKey(variables, name));
    }

    private void putThingNameCertAndPrivateKey(ImmutableMap.Builder<String, String> variables, String name) {
        variables.put("DEVICE_THING_NAME", name);
        variables.put("DEVICE_PUBLIC_CERTIFICATE", ggConstants.getDevicePublicCertificateName(name));
        variables.put("DEVICE_PRIVATE_KEY", ggConstants.getDevicePrivateKeyName(name));
    }

    private void addNormalVariables(ImmutableMap.Builder<String, String> variables, Optional<Architecture> architecture) {
        variables.put("ROOT_CA", ggConstants.getRootCaName());
        variables.put("CONFIG_FILE", ggConstants.getConfigFileName());
        variables.put("CORE_PUBLIC_CERTIFICATE", ggConstants.getCorePublicCertificateName());
        variables.put("CORE_PRIVATE_KEY", ggConstants.getCorePrivateKeyName());
        variables.put("ENDPOINT", iotHelper.getEndpoint());
        variables.put("START_SCRIPT", startScriptName);
        variables.put("STOP_SCRIPT", stopScriptName);
        variables.put("CLEAN_SCRIPT", cleanScriptName);
        variables.put("INSTALL_SCRIPT", installScriptName);
        variables.put("GREENGRASS_DAEMON", ggConstants.getGreengrassDaemonName());
        variables.put("MONITOR_SCRIPT", monitorScriptName);
        variables.put("SYSTEMD_SCRIPT", systemdScriptName);
        variables.put("SYSTEMD_DESTINATION_PATH", LIB_SYSTEMD_SYSTEM_PATH);

        architecture.ifPresent(arch -> variables.put("GG_BITS", arch.getFilename()));
    }

    private String innerGenerateRunScript(String path, Optional<Architecture> architecture, Optional<String> scriptName, Optional<String> thingName, Optional<Set<String>> ggdPipDependencies) {
        String input = resourceHelper.resourceToString(path);

        ImmutableMap.Builder<String, String> variablesBuilder = new ImmutableMap.Builder<>();

        addScriptName(variablesBuilder, scriptName);
        addThingNameVariables(variablesBuilder, thingName);
        addNormalVariables(variablesBuilder, architecture);
        addGgdPipDependencies(variablesBuilder, ggdPipDependencies);

        String output = replaceVariables(variablesBuilder.build(), input);

        return output;
    }

    private void addGgdPipDependencies(ImmutableMap.Builder<String, String> variablesBuilder, Optional<Set<String>> ggdPipDependencies) {
        ggdPipDependencies.ifPresent(list -> createListOfGgdPipDependencies(variablesBuilder, list));

        // Make sure this is filled in so that we don't leave the placeholder string in the template
        if (!variablesBuilder.build().containsKey(GGD_PIP_DEPENDENCIES)) {
            variablesBuilder.put(GGD_PIP_DEPENDENCIES, "");
        }
    }

    private void createListOfGgdPipDependencies(ImmutableMap.Builder<String, String> variablesBuilder, Set<String> list) {
        if (list.isEmpty()) {
            return;
        }

        variablesBuilder.put(GGD_PIP_DEPENDENCIES, String.join(" ", list));
    }

    private String replaceVariables(ImmutableMap<String, String> variables, String input) {
        String output = input;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String pattern = "\\$\\{" + entry.getKey() + "\\}";
            String replacement = Matcher.quoteReplacement(entry.getValue());
            output = output.replaceAll(pattern, replacement);
        }

        return output;
    }
}
