# What is GGP

Greengrass Provisioner allows to easily automate the *Getting Started* provisioning steps of AWS GreenGrass, mentioned in the [official documentation][aws-doc], namely:

[aws-doc]: https://docs.aws.amazon.com/greengrass/latest/developerguide/gg-gs.html

- On the Edge
  - AWS IoT Greengrass core software download and installation 
  - AWS IoT Greengrass core software environment setup (e.g. certificates)
- On the AWS cloud services
  - AWS Greengrass group creation and initial configuration
  - AWS Lambda function creation or e
  - association of one or more Lambda function(s) to a Greengrass group
  - configuration of the Lambda function(s) associated with a Greengrass group (e.g. local resource, subscriptions)
  - deployment of one or more Lambda function(s) to the newly created Greengrass group

All these steps are usually accomplished in sequentially and no  manual intervention is required.

# Main Requirements

To use GGP you need to have:

- An AWS account
- [the AWS Command Line Interface installed][aws1]
- [the AWS Command Line Interface configured][aws2]
- [Docker installed][docker]

[aws1]: https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html
[aws2]: https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html
[docker]: https://docs.docker.com/install/overview/

You can now pull the GGP script locally:

```bash
git clone https://github.com/aws-samples/aws-greengrass-lambda-functions
cd aws-greengrass-lambda-functions
```

Note that there are 2 main git repositories:

- [aws-greengrass-lambda-functions][ggp1]: For those who plan to **use** GGP. It contains the compiled GGP (or better a bash script that pulls locally the Docker image containing the compiled GGP).
- [aws-greengrass-provisioner][ggp2]: For those who plan to **contribute to the development** of GGP. It contains the source code of GGP, the Gradle and Docker build files to automate the building process

[ggp1]: https://github.com/aws-samples/aws-greengrass-lambda-functions
[ggp2]: https://github.com/awslabs/aws-greengrass-provisioner

In this guide you will focus on **using** GGP, so, unless explicitly stated, you should always refer to the [aws-greengrass-lambda-functions][ggp1] repository.

# Example 1 - Raspberry Pi with new Lambda function

## Requirements

- 1 (or more) Raspberry Pi with Raspbian OS installed and pingable
- [SSH enabled][ssh] on the Raspberry Pi
- IP of Raspberry Pi, assigned to the bash variable `$RPI_IP`

[ssh]: https://www.raspberrypi.org/documentation/remote-access/ssh/

## Problem statement

Let us suppose that you have one or more Raspberry Pi(s) (Edge device) that you need to configure to run a new Lambda function, "Hello World", inside a Greengrass Core.

Every 5 seconds the "Hello World" function publishes a message, sent on the following topic:

```json
${AWS_IOT_THING_NAME}/python2/hello/world
```

With a message that looks like:

```json
{"thing_arn": "arn:aws:iot:us-east-1:5xxxxxxxxxx7:thing/xxxxxxxxxxxx_Core", "message": "Hello world! Sent
```

You can find more details about the Lambda function [here][lf1].

[lf1]: https://github.com/aws-samples/aws-greengrass-lambda-functions/tree/master/functions/HelloWorldPython2

## Main steps

From a high-level perspective you need to:

1. Create a Greengrass role
2. Create one Greengrass group per Edge device
3. Create the "Hello World" Lambda Function
4. Associate the Lambda Function to each Greengrass group and configure it (e.g. add Cloud subscriptions)
5. Manually install Greengrass Core software on the Edge, install its dependencies and download the Greengrass group certificates
6. Deploy the Lambda function to the Greengrass group

This process, if done manually, is error-prone and time-consuming. Let us see how you can streamline it using GGP.

## Solution using GGP

Go to the folder where you git cloned the repository and in the console, run:

```bash
./ggp.sh -g test1 -a ARM64 -d deployments/python2-hello-world.conf --script
```

With this command, you provide to the GGP:

- The new Greengrass group name you want to create: `test1`
- The platform of the Edge that you want to setup: `ARM64` (i.e. Raspberry Pi)
- The Lambda function that you want to create: `python2-hello-world`
- The flag `--script` signaling that you want a *1-click bootstrap setup* script for the Edge

Now, you are ready to *ssh copy* the setup script on one of your Raspberry Pis and execute it. Make sure that the Raspberry Pi is turned on, connected to the same Wi-Fi network, pingable, has shh enabled and then run:

```bash
cd build/
scp gg.test1.sh pi@$RPI_IP:~
ssh pi@$RPI_IP:~/gg.test1.sh
```

The bootstrap script will ask you the following three questions:

```bash
$ Install Greengrass? #This unpacks Greengrass and puts all of the files into the /greengrass directory
y
$ Start Greengrass? #This runs ./start.sh to start Greengrass when the installation is complete
y
$ Update dependencies? #This installs all of the dependencies for Greengrass
y
```

Answer y to all three questions.

> Depending on the Raspberry Pi you are using you might get the following message _"You must reboot and re-run this installer run sudo reboot. Wait for the device to restart, ssh back in, and run the installer script again."_

You can now perform the same steps for another RaspberryPi.

## Checking successful deployment

### On the Raspberry Pi

When the script is finished and Greengrass starts it will pull down your deployment of the `python2-hello-world`. Your console will be monitoring the Greengrass logs at this point. You can CTRL-C out of it if you need to get back to the system. You can start the monitoring again by running ./monitor.sh.

After a successful deployment, the last four lines you see in the console should look like:

```bash
[2018-01-17T21:14:01.318Z][INFO]-Trying to subscribe to topic $aws/things/test1_Core-gda/shadow/update/delta
[2018-01-17T21:14:01.355Z][INFO]-Subscribed to : $aws/things/test1_Core-gda/shadow/update/delta
[2018-01-17T21:14:01.355Z][INFO]-Trying to subscribe to topic $aws/things/test1_Core-gda/shadow/get/accepted
[2018-01-17T21:14:01.422Z][INFO]-Subscribed to : $aws/things/test1_Core-gda/shadow/get/accepted
```

### On the AWS IoT Console

On the AWS IoT console on AWS, subscribe to the `hello/world` topic and you should see messages showing up every 5 seconds like this:

```json
Hello world! Sent from Greengrass Core running on platform: Linux-4.9.30-v7+-armv7l-with-debian-9.1 c9855443-944a-4184-992a-b810438c0273 test1_Core arn:aws:iot:us-east-1:5xxxxxxxxxx7:thing/test1_Core
```

## Additional information

### Lambda function configuration

For each new Lambda function, function configuration defaults are stored in `deployments/function.defaults.conf` (in our case [here][hello-conf])

[hello-conf]: https://github.com/aws-samples/aws-greengrass-lambda-functions/blob/master/functions/HelloWorldPython2/function.conf

Note that this is where you configure the main settings of the Lambda function running inside Greengrass Core, such as:

- memorySizeInKb. The memory allocation for the function. More details are available [here][details1]
- pinned. A Lambda function lifecycle can be on-demand or long-lived. A long-lived—or pinned—Lambda function starts automatically after AWS IoT Greengrass starts. More details are available [here][details2]
- timeoutInSeconds. The amount of time before the function or request is terminated. More details are available [here][details1]
- configuration of IoT topics

More details about the GGP implementation of function configurations can be found [here][func-conf].

[func-conf]: https://github.com/awslabs/aws-greengrass-provisioner/blob/master/docs/Concepts.md#function-configurations
[details1]: https://docs.aws.amazon.com/greengrass/latest/developerguide/lambda-group-config.html
[details2]: https://docs.aws.amazon.com/greengrass/latest/developerguide/lambda-functions.html#lambda-lifecycle

### Detailed steps that are executed by GGP

When GGP is executed the following steps will take place:

1. **Docker Image Pull and Execution**
   - Pulling locally the Docker Image that contains the GGP
   - Executing GGP inside the container using your local AWS credentials
2. **Greengrass Role Creation**

   - Creating 2 new Roles:
     - Greengrass Service Role
     - GreengrassCoreRole
   - Updating their IAM Policy
   - Associating them to your account
   - Attaching role policies to them
     - AWSLambdaReadOnlyAccess
     - AWSIoTFullAccess
     - AWSGreengrassFullAccess
     - CloudWatchLogsFullAccess
     - AmazonSageMakerReadOnly
     - AmazonS3ReadOnlyAccess
     - AmazonEC2ContainerRegistryReadOnly

3. **Greengrass Group Creation: _test1_**

   - Creating a Greengrass group
   - Creating IoT Core thing
   - Creating policy for IoT Core
   - Associating the Greengrass role to the group
   - Creating core definition
   - Creating logger definition

4. **Lambda Function Creation: _python2-hello-world_**

   - Creating Lambda role
   - Updating assume role policy for existing role
   - Creating Python function [HelloWorldPython2]
   - Copying Greengrass SDK, Installing Python dependencies, retrieving Python dependencies, packaging function for AWS Lambda, publishing Lambda function version, creating new alias

5. **Lambda function association to Greengrass group**

   - Creating resource definition
   - Creating function definition
   - Set isolation mode: Greengrass container
   - Creating subscriptions
   - Creating device definition
   - Creating subscription definition

6. **Generating script: _build/gg.test1.sh_**

   - Creating group version, adding keys and certificate files to archive, building config.json, adding config.json to archive, getting root CA, adding scripts to archive, adding Greengrass binary to archive
   - Building bootstrap script [build/gg.test1.sh]

7. **Deployment**

# Example 2 - Raspberry Pi with Existing Lambda function

## Requirements

- 1 (or more) Raspberry Pi with Raspbian OS installed and and pingable
- [SSH enabled][ssh] on the Raspberry Pi
- IP of Raspberry Pi, assigned to the bash variable `$RPI_IP`
- 1 or more Lambda function(s) available on your AWS account

[ssh]: https://www.raspberrypi.org/documentation/remote-access/ssh/

## Problem

Let us suppose that you want use GGP with 1 or more existing Lambda(s). In this example, you have 2 lambda already available in your AWS console:

- HelloWorldPython1
- HelloWorldPython2

Each one need to have a ```live``` alias **not** associated to ```Version: $LATEST```, otherwise it GG deployment is bound to fail.

## Main steps
For GGP to work you need to setup the following folder structure:

```bash
|____ggp.sh
|____deployments
| |____deployment.defaults.conf
| |____existingLambda.conf
| |____function.defaults.conf
```

Where the conf file ```existingLambda.conf``` contains the name of the existing Lambda functions.

```bash
conf {
  functions = ["HelloWorldPython1:live","HelloWorldPython2:live"]
}
```

If the name of your lambda contains additional characters (e.g. dynamically appended by Cloud Formation), you can use the wildcard symbol ```~``` to uniquely identify your lambda. For example, the following code will find the Lambda function named `123_lambda_123`:
 ```bash
conf {
  functions = ["~lambda~:live"]
}
```
> Note: if more than one function is found, GGP will fail.

Now, you need to configure the GG group. In the previous example, the configuration was placed inside the ```function.conf```. If the lambda function already exists in the AWS console, then, you need to place the GG configuration inside the *Enviromental Variable*  ```GGP_FUNCTION_CONF```.
For example:

```bash
conf {  language = "PYTHON2_7"  functionName = "HelloWorldPython"  handlerName = "HelloWorldPython.function_handler"  aliasName = "PROD"  memorySizeInKb = 131072  pinned = true  timeoutInSeconds = 60  fromCloudSubscriptions = []  toCloudSubscriptions = [${AWS_IOT_THING_NAME}"/python2/hello/world"]  outputTopics = []  inputTopics = []}
```

You can find more information about the parameters at this link.

Now, you can run the GGP using the standard command:
```bash
./ggp.sh -g test2 -a ARM64 -d deployments/existingLambda.conf.conf --script
```

## Additional Information
If you have a CloudFormation template associated with your Lambda you can even add the GGP parameters to the Environment Variable ```GGP_FUNCTION_CONF``` inside the template. For example:

```YAML
...

  LambdaFunction:
    Properties:
      CodeUri:
        Bucket: ...
        Key: ...
      Handler: handlers.lambda_handler
      MemorySize: 512
      Role: ...
      Runtime: python2.7
      Timeout: 150
      Environment:
          Variables: 
            GGP_FUNCTION_CONF: conf {  language = "PYTHON2_7"  functionName = "HelloWorldPython"  handlerName = "HelloWorldPython.function_handler"  aliasName = "PROD"  memorySizeInKb = 131072  pinned = true  timeoutInSeconds = 60  fromCloudSubscriptions = []  toCloudSubscriptions = [${AWS_IOT_THING_NAME}"/python2/hello/world"]  outputTopics = []  inputTopics = []}

...
```
