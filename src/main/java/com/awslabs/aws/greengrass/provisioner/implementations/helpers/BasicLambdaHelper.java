package com.awslabs.aws.greengrass.provisioner.implementations.helpers;

import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.Runtime;
import com.amazonaws.services.lambda.model.*;
import com.awslabs.aws.greengrass.provisioner.data.LambdaFunctionArnInfo;
import com.awslabs.aws.greengrass.provisioner.data.conf.FunctionConf;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.GradleBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.MavenBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.NodeBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.builders.PythonBuilder;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.IoHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LambdaHelper;
import com.awslabs.aws.greengrass.provisioner.interfaces.helpers.LoggingHelper;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Optional;

@Slf4j

public class BasicLambdaHelper implements LambdaHelper {
    @Inject
    AWSLambdaClient awsLambdaClient;
    @Inject
    IoHelper ioHelper;
    @Inject
    MavenBuilder mavenBuilder;
    @Inject
    GradleBuilder gradleBuilder;
    @Inject
    PythonBuilder pythonBuilder;
    @Inject
    NodeBuilder nodeBuilder;
    @Inject
    LoggingHelper loggingHelper;

    @Inject
    public BasicLambdaHelper() {
    }

    private boolean functionExists(String functionName) {
        GetFunctionRequest getFunctionRequest = new GetFunctionRequest()
                .withFunctionName(functionName);

        try {
            awsLambdaClient.getFunction(getFunctionRequest);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreateJavaFunctionIfNecessary(FunctionConf functionConf, Role role) {
        String zipFilePath;

        if (mavenBuilder.isMavenFunction(functionConf)) {
            /*
            mavenBuilder.buildJavaFunctionIfNecessary(functionConf);

            zipFilePath = mavenBuilder.getArchivePath(functionConf);
            */
            throw new UnsupportedOperationException("This function [" + functionConf.getFunctionName() + "] is a Maven project but Maven support is currently disabled.  If you need this feature please file a Github issue.");
        } else if (gradleBuilder.isGradleFunction(functionConf)) {
            gradleBuilder.buildJavaFunctionIfNecessary(functionConf);

            zipFilePath = gradleBuilder.getArchivePath(functionConf);
        } else {
            throw new UnsupportedOperationException("This function [" + functionConf.getFunctionName() + "] is neither a Maven project nor a Gradle project.  It cannot be built automatically.");
        }

        return createFunctionIfNecessary(functionConf, Runtime.Java8, role, zipFilePath);
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreatePythonFunctionIfNecessary(FunctionConf functionConf, Role role) {
        Optional<String> error = pythonBuilder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return LambdaFunctionArnInfo.builder()
                    .error(error).build();
        }

        pythonBuilder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = pythonBuilder.getArchivePath(functionConf);
        LambdaFunctionArnInfo result = createFunctionIfNecessary(functionConf, Runtime.Python27, role, zipFilePath);
        return result;
    }

    @Override
    public LambdaFunctionArnInfo buildAndCreateNodeFunctionIfNecessary(FunctionConf functionConf, Role role) {
        Optional<String> error = nodeBuilder.verifyHandlerExists(functionConf);

        if (error.isPresent()) {
            return LambdaFunctionArnInfo.builder()
                    .error(error).build();
        }

        nodeBuilder.buildFunctionIfNecessary(functionConf);

        String zipFilePath = nodeBuilder.getArchivePath(functionConf);
        LambdaFunctionArnInfo result = createFunctionIfNecessary(functionConf, Runtime.Nodejs610, role, zipFilePath);
        return result;
    }

    @Override
    public LambdaFunctionArnInfo createFunctionIfNecessary(FunctionConf functionConf, Runtime runtime, Role role, String zipFilePath) {
        String baseFunctionName = functionConf.getFunctionName();
        String groupFunctionName = getFunctionName(functionConf);

        if (functionExists(groupFunctionName)) {
            loggingHelper.logInfoWithName(log, baseFunctionName, "Deleting existing Lambda function");
            DeleteFunctionRequest deleteFunctionRequest = new DeleteFunctionRequest()
                    .withFunctionName(groupFunctionName);

            awsLambdaClient.deleteFunction(deleteFunctionRequest);
        }

        FunctionCode functionCode = new FunctionCode()
                .withZipFile(ByteBuffer.wrap(ioHelper.readFile(zipFilePath)));

        loggingHelper.logInfoWithName(log, baseFunctionName, "Creating new Lambda function");
        CreateFunctionRequest createFunctionRequest = new CreateFunctionRequest()
                .withFunctionName(groupFunctionName)
                .withRuntime(runtime)
                .withRole(role.getArn())
                .withHandler(functionConf.getHandlerName())
                .withCode(functionCode);

        boolean created = false;
        int counter = 0;

        // Make sure multiple threads don't do this at the same time
        synchronized (this) {
            while (!created) {
                try {
                    awsLambdaClient.createFunction(createFunctionRequest);
                    created = true;
                } catch (InvalidParameterValueException e) {
                    if (!e.getMessage().startsWith("The role defined for the function cannot be assumed by Lambda.")) {
                        throw e;
                    }

                    counter++;

                    if (counter > 10) {
                        throw new UnsupportedOperationException("Something went wrong with the Lambda IAM role, try again later");
                    }

                    log.warn("Waiting for IAM role to be available to AWS Lambda...");

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }

        loggingHelper.logInfoWithName(log, baseFunctionName, "Publishing Lambda function version");
        PublishVersionResult publishVersionResult = publishFunctionVersion(groupFunctionName);

        String qualifier = publishVersionResult.getVersion();
        String qualifiedArn = publishVersionResult.getFunctionArn();
        String baseArn = qualifiedArn.replaceAll(":" + qualifier + "$", "");

        LambdaFunctionArnInfo lambdaFunctionArnInfo = LambdaFunctionArnInfo.builder()
                .qualifier(qualifier)
                .qualifiedArn(qualifiedArn)
                .baseArn(baseArn)
                .build();

        return lambdaFunctionArnInfo;
    }

    @Override
    public PublishVersionResult publishFunctionVersion(String groupFunctionName) {
        PublishVersionRequest publishVersionRequest = new PublishVersionRequest()
                .withFunctionName(groupFunctionName);

        return awsLambdaClient.publishVersion(publishVersionRequest);
    }

    private boolean aliasExists(FunctionConf functionConf) {
        return aliasExists(getFunctionName(functionConf), functionConf.getAliasName());
    }

    @Override
    public boolean aliasExists(String functionName, String aliasName) {
        GetAliasRequest getAliasRequest = new GetAliasRequest()
                .withFunctionName(functionName)
                .withName(aliasName);

        try {
            awsLambdaClient.getAlias(getAliasRequest);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    private String getFunctionName(FunctionConf functionConf) {
        return getFunctionName(functionConf.getGroupName(), functionConf.getFunctionName());
    }

    private String getFunctionName(String groupName, String baseFunctionName) {
        return String.join("-", groupName, baseFunctionName);
    }

    @Override
    public String createAlias(Optional<String> groupName, String baseFunctionName, String functionVersion, String aliasName) {
        String groupFunctionName = baseFunctionName;

        if (groupName.isPresent()) {
            groupFunctionName = getFunctionName(groupName.get(), baseFunctionName);
        }

        if (aliasExists(groupFunctionName, aliasName)) {
            loggingHelper.logInfoWithName(log, baseFunctionName, "Deleting existing alias");
            DeleteAliasRequest deleteAliasRequest = new DeleteAliasRequest()
                    .withFunctionName(groupFunctionName)
                    .withName(aliasName);
            awsLambdaClient.deleteAlias(deleteAliasRequest);
        }

        loggingHelper.logInfoWithName(log, baseFunctionName, "Creating new alias");

        CreateAliasRequest createAliasRequest = new CreateAliasRequest()
                .withFunctionName(groupFunctionName)
                .withName(aliasName)
                .withFunctionVersion(functionVersion);

        CreateAliasResult createAliasResult = awsLambdaClient.createAlias(createAliasRequest);

        return createAliasResult.getAliasArn();
    }

    @Override
    public String createAlias(FunctionConf functionConf, String functionVersion) {
        return createAlias(Optional.of(functionConf.getGroupName()), functionConf.getFunctionName(), functionVersion, functionConf.getAliasName());
    }

    @Override
    public Optional<GetFunctionResult> getFunction(String functionName) {
        GetFunctionRequest getFunctionRequest = new GetFunctionRequest()
                .withFunctionName(functionName);

        try {
            GetFunctionResult getFunctionResult = awsLambdaClient.getFunction(getFunctionRequest);

            return Optional.of(getFunctionResult);
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteAlias(String functionArn) {
        String temp = functionArn.substring(0, functionArn.lastIndexOf(":"));
        String aliasName = functionArn.substring(functionArn.lastIndexOf(":") + 1);
        String functionName = temp.substring(temp.lastIndexOf(":") + 1);

        DeleteAliasRequest deleteAliasRequest = new DeleteAliasRequest()
                .withFunctionName(functionName)
                .withName(aliasName);

        awsLambdaClient.deleteAlias(deleteAliasRequest);
    }
}
