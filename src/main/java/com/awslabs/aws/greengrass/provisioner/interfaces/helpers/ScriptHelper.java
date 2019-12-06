package com.awslabs.aws.greengrass.provisioner.interfaces.helpers;

import com.awslabs.aws.greengrass.provisioner.data.Architecture;

import java.util.Optional;
import java.util.Set;

public interface ScriptHelper {
    String getStartScriptName();

    String generateStartScript(Architecture architecture);

    String getStopScriptName();

    String generateStopScript(Architecture architecture);

    String getCleanScriptName();

    String generateCleanScript(Architecture architecture, String ggShScriptName);

    String generateCredentialsScript();

    String generateUpdateDependenciesScript();

    String generateGgScript(Architecture architecture, Set<String> ggdPipDependencies);

    String getCredentialsScriptName();

    String getUpdateDependenciesScriptName();

    String getMonitorScriptName();

    String generateMonitorScript(Architecture architecture);

    String getSystemdScriptName();

    String generateSystemdScript();

    String generateRunScript(Optional<Architecture> architecture, String filename, String thingName);
}
