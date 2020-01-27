# Supported GGP Environment Variables for existing Lambda deployment
If you want to create a new GG group using an existing Lambda function you need to set a ```GGP_FUNCTION_CONF``` environment variable inside your Lambda.
For example, given the following configuration stored as `value` of the ```GGP_FUNCTION_CONF```:
```bash
conf {
    language = "PYTHON2_7",
    functionName = "existingLambda1",
    handlerName = "existingLambda1.lambda_handler",
    aliasName = "live",
    pinned = true,
    functionBinary = false,
    greengrassContainer = true,
    memorySizeInKb = 30000,
    timeoutInSeconds = 3,
    groupOwnerSetting = "false",
    fromCloudSubscriptions = ["existingLambda1/out/+"],
    toCloudSubscriptions = [],
    outputTopics = [],
    inputTopics = [],
    localVolumeResources = [{
        resourceName = "/volumelambda",
        sourcePath = "/volumelambda/hello",
        destinationPath = "/volumelambda",
        groupOwnerSetting = "false",
        readWrite = true}
        ],
    environmentVariables = {
        "env1" = "val1/scripts/",
        "env2" = "val2"
        }
    }
```
The parameters are:
- ```language```: currently AWS IoT Greengrass supports Lambda functions authored in the following languages:
  - Python 2.7 (```PYTHON2_7```) and 3.7 (```PYTHON3_7```)
  - Node v6.10 (```NODEJS6_10```) and Node v8.10 (```NODEJS6_10```)
  - Java 8 (```JAVA8```)
  - C, C++ and any language that supports importing C libraries (```EXECUTABLE```)
- ```functionName```: name of the function
- [handlerName][handlerName]: when you create a Lambda function, you have to specify a handler, which is a function that AWS invokes when the Lambda is is executed.
- [aliasName][aliasName]: you can create one or more aliases for your AWS Lambda function. A Lambda alias is like a pointer to a specific Lambda function version. Users can access the function version using the alias ARN.
- [pinned][pinned]: True if the function is pinned. Pinned means the function is long-lived and starts when the core starts.
- [functionBinary][functionBinary]: this corresponds to the ```EncodingType```. The expected encoding type of the input payload for the function. The default is json.
- [greengrassContainer][greengrassContainer]: this corresponds to ```IsolationMode```. Specifies whether the Lambda function runs in a Greengrass container (default) or without containerization. Unless your scenario requires that you run without containerization, we recommend that you run in a Greengrass container. Omit this value to run the Lambda function with the default containerization for the group.
- [memorySizeInKb][memorySizeInKb]: The memory size, in KB, required by the function. This setting does not apply and should be cleared when you run the Lambda function without containerization
- [timeoutInSeconds][timeoutInSeconds]: the allowed function execution time, after which Lambda should terminate the function. This timeout still applies to pinned Lambda functions for each request.
- [groupOwnerSetting][groupOwnerSetting]: this corresponds to ```RunAs```. Specifies the user and group whose permissions are used when running the Lambda function. You can specify one or both values to override the default values. To minimize the risk of unintended changes or malicious attacks, we recommend that you avoid running as root unless absolutely necessary. To run as root, you must update config.json in greengrass-root/config to set allowFunctionsToRunAsRoot to yes.
- [From/To CloudSubscriptions][CloudSubscriptions]: it configures the the subscription that allows the Lambda function to communicate with AWS IoT.
- [Input/Output Topics][topics]: it configures the the subscription that allows the Lambda function to communicate with other Lambda functions.
- [localVolumeResources][localVolumeResources]: Attributes that define a local volume resource:
- [environmentVariables][environmentVariables]: Environment variables for the Lambda function's configuration.

[handlerName]: https://docs.aws.amazon.com/lambda/latest/dg/python-programming-model-handler-types.html
[aliasName]: https://docs.aws.amazon.com/lambda/latest/dg/configuration-aliases.html
[pinned]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-functionconfiguration.html
[functionBinary]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-functionconfiguration.html
[greengrassContainer]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-functionconfiguration.html
[memorySizeInKb]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-functionconfiguration.html
[timeoutInSeconds]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-functionconfiguration.html
[groupOwnerSetting]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-functionconfiguration.html
[CloudSubscriptions]: https://docs.aws.amazon.com/greengrass/latest/developerguide/config-lambda.html
[topics]: https://docs.aws.amazon.com/greengrass/latest/developerguide/config_subs.html
[localVolumeResources]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-localvolumeresourcedata.html
[environmentVariables]: https://docs.aws.amazon.com/greengrass/latest/apireference/definitions-functionconfiguration.html