Lambda support for GGP
======================

The files in this directory allow a customer to launch GGP as a Lambda function in their account via CloudFormation.

Lambda support will initially be limited to empty deployments but may be expanded later. The use case for empty deployments
is to allow a customer to bootstrap Greengrass and then configure it running GGP on a different system.

What is here?
-------------

- `launch-lambda-stack-for-ggp.sh` - Builds GGP and launch the CloudFormation stack to get it
set up as a Lambda function. It does the following tasks:
  - Creates a [Greengrass service role](https://docs.aws.amazon.com/greengrass/latest/developerguide/service-role.html) and associates that service role with the Greengrass service
if necessary. This is done with two Lambda functions. The first Lambda function detects if the customer already has a
service role for Greengrass set up. The second Lambda function associates the role if no previous role was set up. The
Lambda function will also disassociate the role from the Greengrass service but *only if the associated role is the role it created*.
This avoids potentially breaking a customer's configuration if they used a different role.
  - Builds [the minimal IoT policy for Greengrass](https://docs.aws.amazon.com/greengrass/latest/developerguide/gg-sec.html#gg-config-sec-min-iot-policy)
  - Builds a minimal role for the Greengrass core
  - Adds GGP as a Lambda function with the permissions necessary to create empty deployments

- `invoke-lambda-function.sh` - Invokes GGP as a Lambda function and attempt to create a new Greengrass
group with a random name and an empty deployment. It can be used to validate that the function works.

- `extract-from-outfile.sh` - Extracts files from the output JSON saved from the `invoke-lambda-function.sh` script

## 1. Deploy the lambda-support stack
```
$ ./launch-lambda-stack-for-ggp.sh <my-bucket> ggp-lambda-support-stack 2.16.63
```

## 2. Provision the greengrass group
```
$ STACK_NAME=ggp-lambda-support-stack GROUP_NAME=g1 EVENT_TYPE=Provision ./invoke-lambda-function.sh
```

## 3. Deployment for the provisioned greengrass group
```
$ EVENT_TYPE=Deploy ./invoke-lambda-function.sh
```

## Reference
### 1. npm-layer to enable node.js function for lambda-support
https://github.com/sambaiz/npm-lambda-layer

### 2. aws-sdk-java-v2
https://github.com/aws/aws-sdk-java-v2