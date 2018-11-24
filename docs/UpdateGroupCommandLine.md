# Update group command-line options

**Note: To update a group you must use the `--update-group` option.  It is a flag and does not take any arguments.**

**Note: The update group features are intended for ad-hoc development and testing.  Switch to using deployments when you
are ready to go to production.  Deployments are more reproducible.  If deployments are lacking a feature you need please
create a Github issue and request it!**

## Group name

Required: Always

Long form: `--group-name`

Short form: `-g`

Specifies the name of the group that you want to work with.

## Add subscription

Long form: `--add-subscription`

This is a flag to indicate that a subscription will be added.

## Remove subscription

Long form: `--remove-subscription`

This is a flag to indicate that a subscription will be removed.

## Subscription source

Long form: `--subscription-source`

This value is the source of a subscription.  This can be either a function, device, or the special value `cloud`.

## Subscription target

Long form: `--subscription-target`

This value is the target of a subscription.  This can be either a function, device, or the special value `cloud`.

## Subscription source and target notes

Sources and targets that are functions or devices must match a function or device already in the group.  For simplicity
they can be partial matches.  Partial matches are checked against the end of the name.

For example, if you have a function called `GROUP_NAME-HelloWorldPython:PROD` you can just specify `HelloWorldPython:PROD`.
If you have a device called `ReallyLongDeviceName` you can just specify `DeviceName`.

If the partial match matches multiple functions or devices then the update will be rejected.

## Subscription subject

Long form: `--subscription-subject`

This value is the topic (or topic wildcard) that the subscription should match.

## Add device

Long form: `--add-device`

This value is the name of the device to be added from the group.  This will create a new thing, private key,
certificate, and policy the first time it is called for a specific device name if it doesn't look like a thing ARN.
These credentials will be cached so they can be reused later if a device is removed and added back to the group.

If the device name looks like a thing ARN (it contains at least one slash) then the device with that thing ARN will be
used.

## Remove device

Long form: `--remove-device`

This value is the name of the device to be removed from the group.

**Note: All subscriptions related to this device will be removed as well.**

## Add function

Long form: `--add-function`

The name of an AWS Lambda function (not the ARN, no version information) to add to a group.  Additional options for
adding a function are:

- Function alias - A new alias for the function to be added to the group
- Function binary - A flag that indicates the function expects binary payloads
- Function pinned - A flag that indicates the function is long-running/pinned

## Remove function

Long form: `--remove-function`

The name of an AWS Lambda function (not the ARN, no version information) to remove from a group.  Additional options for
adding a function are:

- Function alias - The alias of the function to be removed from the group

**Note: All subscriptions related to this function will be removed as well.**

**Note: The alias will be removed in AWS Lambda if this operation is successful.**

## Function alias

Long form: `--function-alias`

The alias for the function to be added/removed from the group.  This must be a new alias when adding a function to the
group to avoid impacting other Greengrass Groups.

## Function binary

Long form: `--function-alias`

Include this flag if a function expects binary payloads.  If this flag is omitted the function will be configured to
accept JSON payloads.

## Function pinned

Long form: `--function-pinned`

Include this flag if a function is supposed to be a long-running/pinned function.  If this flag is omitted the function
will be configured to be event driven only.
