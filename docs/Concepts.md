# Concepts

## Deployment configurations

Deployment configurations specify:

- Which functions to deploy, if any
- Role and policy information for the core, if different than the defaults
- Which Greengrass Device scripts to generate when the `--ggd` option is specified, if any

Deployment configuration defaults are stored in `deployments/deployment.defaults.conf`.

## Function configurations

Function configurations specify:

- The language to use (`Java`, `Python`, or `Node`)
- The function name which is used to determine its name in AWS Lambda.  A function's final name in AWS Lambda will be
`GROUP_NAME-FUNCTION_NAME` where `GROUP_NAME` is the name of the group and `FUNCTION_NAME` is the value specified in the
function.conf file.  This avoids naming conflicts when running multiple cores with the same functions.
- The handler name where Lambda will send events.  This must be specified and the function must exist even for pinned
functions.
- The alias name.  Functions are built locally, created in AWS Lambda, a version of the function is published, and then
an alias for the version that was published is created.  This makes the subscription table easier to manage.
- The memory size in kilobytes
- Whether the function is pinned (long-running) or not
- The timeout in seconds for the function to process incoming events
- To/From cloud topics (see below)
- Input and output topics (see below)
- Local resource information
- Machine learning resource information (Files in S3 or SageMaker training job ARNs)

Function configuration defaults are stored in `functions/deployment.defaults.conf`.

### Input topics

Input topics are topics that a local Lambda function expects to receive messages on from other local Lambda functions.
If a Lambda function in a deployment specifies a topic as an input topic that another Lambda function in the same
deployment specifies as an output topic then the two functions will be wired together in the subscription table.

### Output topics

Output topics are topics that a local Lambda function expects to send messages on to other local Lambda functions.
If a Lambda function in a deployment specifies a topic as an output topic that another Lambda function in the same
deployment specifies as an input topic then the two functions will be wired together in the subscription table.

### Input and output topic topic examples and limitations

For example, `functionA` lists `sensor/data` as an input topic and `functionB` lists `sensor/data` as an output topic.
A subscription table entry will be created with the source as `functionB`, the destination as `functionA`, and the
subject as `sensor/data`.

Currently wildcard support is limited and only works on exact matches.  For example, if a function lists `sensor/#` as
an input topic, it will only be wired up to functions that list `sensor/#` as an output topic.  It will not be wired up
to a function that lists `sensor/1/temp` as an output topic.

### To/From cloud subscriptions

To/From cloud subscriptions are topics that a local Lambda function expects to send messages to that will be routed to the
cloud or that it expects to receive messages on that will be received from the cloud.

Wildcards for to/from cloud topics work as expected.

### Connected shadows

Connected shadows are thing shadows that a local Lambda function needs to interact with.  By specifying that a Lambda
function has a connected shadow the subscription table will have two entries added to it.  The first entry specifies the
Lambda function as the source, the local shadow service as the destination, and `$aws/things/CONNECTED_SHADOW/shadow/#`
as the subject. The second entry specifies the Lambda function as the destination, the local shadow service as the
source, and `$aws/things/CONNECTED_SHADOW/shadow/#` as the subject.  `CONNECTED_SHADOW` is replaced with the actual
thing name.

## Greengrass Device (GGD) scripts

Greengrass Device scripts are scripts (currently Python only) that can be packaged with credentials that allow them to
connect to the Greengrass Core as Greengrass Devices.  These are simulated devices that can be used to test the core's
connectivity info and Greengrass discovery and they can also serve as a way to interact with the core outside of the
core's Lambda runtime.  These scripts run as normal Linux processes and can be run from anywhere that the core can be
reached, either on the core itself or on a remote machine.

# Environment variables available to Lambda functions

For certain use cases it is convenient to have access to some information about the core that isn't currently available
at runtime.  To fix this we have added several environment variables to each function.

## Group ID

The group ID of the group is available to Lambda functions in the `GROUP_ID` environment variable.
This can be useful to construct diagnostic messages.

## Thing name of the core

The thing name in AWS IoT of the core is available to Lambda functions in the `AWS_IOT_THING_NAME` environment variable.
This can be useful to construct diagnostic messages and build topic hierarchies unique to a group to keep messages from
different groups separate.

## Thing ARN of the core

The thing ARN in AWS IoT of the core is available to Lambda functions in the `AWS_IOT_THING_ARN` environment variable.
This can be useful to construct diagnostic messages.

## Group name

The AWS Greengrass group name is available to Lambda functions in the `AWS_GREENGRASS_GROUP_NAME` environment variable.
This can be useful to construct diagnostic messages and build topic hierarchies unique to a group to keep messages from
different groups separate.

## Connected shadows

The name of any AWS IoT thing whose shadow is connected to a Lambda function is in the `CONNECTED_SHADOW_*` variables.
These variables start at zero (0) and go up until each connected shadow has its own variable.

For example, if a Lambda function had two thing shadows connected to it, deviceA and deviceB, then it would have two
environment variables available to it:

`CONNECTED_SHADOW_0` would contain `deviceA`
`CONNECTED_SHADOW_1` would contain `deviceB`

**Note:** The order of this list is not guaranteed so it may change from deployment to deployment.

## Local Lambdas

The names of all of the functions deployed in a group are available to all other Lambda functions in the
`LOCAL_LAMBDA_*` variables.  For example, if you deploy a group with the `HelloWorldPython` and `HelloWorldNode`
functions each of those functions will have two environment variables added to their environments:

- `LOCAL_LAMBDA_HelloWorldPython` - this contains the function ARN of the Hello World Python function
- `LOCAL_LAMBDA_HelloWorldNode` - this contains the function ARN of the Hello World Node function

This is useful when functions need to invoke other Lambda functions but they may not know the exact version that is in
use.  The function ARN contains this explicitly so functions can look up each other by their base name (Lambda function
name without the group name prefix).
