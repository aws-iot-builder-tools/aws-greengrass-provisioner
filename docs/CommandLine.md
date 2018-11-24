# Command-line options available

## Modes of operation

There are currently three modes of operation for GGP and therefore three sets of command-line options.  Each of the
documents below describes the options for the different modes.

- [Deployment](/docs/DeploymentCommandLine.md)
- [Update Group](/docs/UpdateGroupCommandLine.md)
- [Query Group](/docs/QueryGroupCommandLine.md)

## Examples

Dislike dry and dense CLI docs?  Check out some examples below.

Want an example added?  Open a Github issue and describe what you're looking for.

## Deployment examples

**Note:** You will need the deployments and functions directories from the AWS Labs Greengrass functions repo.

**Remember: Any references to `GGP` in commands in this documentation must be replaced with the appropriate command for
your environment (e.g. `./deploy.sh`, `java -jar ...`, etc.)**

### Deploy or redeploy an empty group (no functions) and create a bootstrap script

**Note: After the bootstrap script is used or the Greengrass Core is running you can omit the --script option**

X86_64:

```bash
GGP -g test-group -a X86_64 -d deployments/empty.conf --script
```

ARM32:

```bash
GGP -g test-group -a ARM32 -d deployments/empty.conf --script
```

This will create a group called `test-group`, if it doesn't exist already, and set the group configuration so there are
no functions deployed.  It will also output a bootstrap script named `build/gg.test-group.sh` that can be used to
bootstrap a Raspberry Pi running Raspbian (if ARM32 architecture is specified), a system running Ubuntu,
or a system running Amazon Linux.

Redeployments of an existing group will happen without re-running the bootstrap script.

### Deploy or redeploy a group with the Python Hello World function

X86_64:

```bash
GGP -g test-group -a X86_64 -d deployments/python-hello-world.conf --script
```

ARM32:

```bash
GGP -g test-group -a ARM32 -d deployments/python-hello-world.conf --script
```

Redeployments of an existing group will happen without re-running the bootstrap script.

## Query examples

### List the Lambda functions in a group

```bash
GGP -g test-group --query-group --list-functions
```

### List the Lambda functions in a group

```bash
GGP -g test-group --query-group --list-subscriptions
```

### List the Lambda devices in a group

```bash
GGP -g test-group --query-group --list-devices
```

## Update examples

### Add a new device called test-device to the group

```bash
GGP -g test-group --update-group --add-device test-device
```

This will add the device to the group and redeploy it.  It will place the private key information in
`build/test-device.pem.key` and the public signed certificate in `build/test-device.pem.crt`

### Add an existing device called test-device to the group

```bash
GGP -g test-group --update-group --add-device test-device
```

This will add the device to the group and redeploy it.  It will place the private key information in
`build/test-device.pem.key` and the public signed certificate in `build/test-device.pem.crt`

### Add a subscription that sends messages from a device to the cloud

This subscription will relay all messages from test-device to the cloud on the test-device-message/# topic hierarchy.

```bash
GGP -g test-group --update-group --add-subscription --subscription-source test-device --subscription-target cloud --subscription-subject test-device-messages/#
```

### Add a subscription that sends messages from the cloud to a device

This subscription will relay all messages from the cloud on the test-device-inbound/# topic hierarchy to test-device.

```bash
GGP -g test-group --update-group --add-subscription --subscription-source cloud --subscription-target test-device --subscription-subject test-device-inbound/#
```

### Remove a subscription from a device

```bash
GGP -g test-group --update-group --remove-subscription --subscription-source test-device --subscription-target cloud --subscription-subject test-device-messages/#
```

### Remove a subscription from a function

```bash
GGP -g test-group --update-group --remove-subscription --subscription-source HelloWorldPython:PROD --subscription-target cloud --subscription-subject test-group_Core/python/hello/world
```

### Add an existing AWS Lambda function to a group

This will add the function `ExistingLambdaName` to the specified group with the alias `PROD`.  This alias must not exist
already.

```bash
GGP -g test-group --update-group --add-function ExistingLambdaName --function-alias PROD
```

### Remove a Lambda function from a group

This will remove the function `ExistingLambdaName` with the alias `PROD` from the specified group.  It will also delete
the alias in AWS Lambda.

```bash
GGP -g test-group --update-group --remove-function ExistingLambdaName --function-alias PROD
```
