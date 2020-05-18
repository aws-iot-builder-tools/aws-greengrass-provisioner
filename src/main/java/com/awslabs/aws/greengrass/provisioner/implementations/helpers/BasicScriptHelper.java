package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.GGConstants;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.JavaResourceHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.ScriptHelper;
import com.awslabs.iot.data.ThingName;
import com.awslabs.iot.data.V2IotEndpointType;
import com.awslabs.iot.helpers.interfaces.V2IotHelper;
import com.google.common.collect.ImmutableMap;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;


public class BasicScriptHelper implements ScriptHelper {
    private static final String LIB_SYSTEMD_SYSTEM_PATH = "/lib/systemd/system";
    private final String shellPath = "shell/";
    private final String startTemplatePath = String.join("", shellPath, getStartScriptName(), ".in");
    private final String stopTemplatePath = String.join("", shellPath, getStopScriptName(), ".in");
    private final String cleanTemplatePath = String.join("", shellPath, getCleanScriptName(), ".in");
    private final String credentialsScriptPath = String.join("", shellPath, getCredentialsScriptName());
    private final String updateDependenciesScriptPath = String.join("", shellPath, getUpdateDependenciesScriptName(), ".in");
    private final String ggScriptTemplatePath = String.join("", shellPath, "template.sh.in");
    private final String monitorTemplatePath = String.join("", shellPath, getMonitorScriptName(), ".in");
    private final String systemdTemplatePath = String.join("", shellPath, getSystemdScriptName(), ".in");
    @Inject
    GGConstants ggConstants;
    @Inject
    V2IotHelper v2IotHelper;
    @Inject
    JavaResourceHelper javaResourceHelper;

    @Inject
    public BasicScriptHelper() {
    }

    @Override
    public String getCleanScriptName() {
        return "clean.sh";
    }

    @Override
    public String getCredentialsScriptName() {
        return "credentials.sh";
    }

    @Override
    public String getUpdateDependenciesScriptName() {
        return "update-dependencies.sh";
    }

    @Override
    public String getMonitorScriptName() {
        return "monitor.sh";
    }

    @Override
    public String getSystemdScriptName() {
        return "greengrass.service";
    }

    @Override
    public String getStartScriptName() {
        return "start.sh";
    }

    @Override
    public String getStopScriptName() {
        return "stop.sh";
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
    public String generateCredentialsScript() {
        return innerGenerateRunScriptWithArchitecture(credentialsScriptPath, Optional.empty(), Optional.empty());
    }

    @Override
    public String generateUpdateDependenciesScript() {
        return innerGenerateRunScriptWithArchitecture(updateDependenciesScriptPath, Optional.empty(), Optional.empty());
    }

    @Override
    public String generateGgScript(Architecture architecture) {
        return innerGenerateRunScriptWithArchitecture(ggScriptTemplatePath, Optional.of(architecture));
    }

    @Override
    public String generateMonitorScript(Architecture architecture) {
        return innerGenerateRunScriptWithArchitecture(monitorTemplatePath, Optional.ofNullable(architecture));
    }

    @Override
    public String generateSystemdScript() {
        return innerGenerateRunScript(systemdTemplatePath, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private String innerGenerateRunScriptWithArchitecture(String path, Optional<Architecture> architecture) {
        return innerGenerateRunScript(path, architecture, Optional.empty(), Optional.empty());
    }

    private String innerGenerateRunScriptWithArchitecture(String path, Optional<Architecture> architecture, Optional<String> scriptName) {
        return innerGenerateRunScript(path, architecture, scriptName, Optional.empty());
    }

    private void addScriptName(ImmutableMap.Builder<String, String> variables, Optional<String> scriptName) {
        scriptName.ifPresent(name -> putScriptNameAndGgSh(variables, name));
    }

    private void putScriptNameAndGgSh(ImmutableMap.Builder<String, String> variables, String name) {
        variables.put("SCRIPT_NAME", name);
        variables.put("GG_SH", name);
    }

    private void addNormalVariables
            (ImmutableMap.Builder<String, String> variables, Optional<Architecture> architecture) {
        variables.put("ENDPOINT", v2IotHelper.getEndpoint(V2IotEndpointType.DATA_ATS));
        variables.put("START_SCRIPT", getStartScriptName());
        variables.put("STOP_SCRIPT", getStopScriptName());
        variables.put("CLEAN_SCRIPT", getCleanScriptName());
        variables.put("GREENGRASS_DAEMON", ggConstants.getGreengrassDaemonName());
        variables.put("MONITOR_SCRIPT", getMonitorScriptName());
        variables.put("SYSTEMD_SCRIPT", getSystemdScriptName());
        variables.put("CREDENTIALS_SCRIPT", getCredentialsScriptName());
        variables.put("UPDATE_DEPENDENCIES_SCRIPT", getUpdateDependenciesScriptName());
        variables.put("SYSTEMD_DESTINATION_PATH", LIB_SYSTEMD_SYSTEM_PATH);

        architecture.ifPresent(arch -> variables.put("GG_BITS", arch.getFilename()));
    }

    private String innerGenerateRunScript(String path, Optional<Architecture> architecture, Optional<String> scriptName, Optional<ThingName> thingName) {
        String input = javaResourceHelper.resourceToString(path);

        ImmutableMap.Builder<String, String> variablesBuilder = new ImmutableMap.Builder<>();

        addScriptName(variablesBuilder, scriptName);
        addNormalVariables(variablesBuilder, architecture);

        String output = replaceVariables(variablesBuilder.build(), input);

        return output;
    }

    private String replaceVariables(ImmutableMap<String, String> variables, String input) {
        String output = input;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String pattern = String.join("", "\\$\\{", entry.getKey(), "\\}");
            String replacement = Matcher.quoteReplacement(entry.getValue());
            output = output.replaceAll(pattern, replacement);
        }

        return output;
    }
}
