# Query group command-line options

**Note: To query a group you must use the `--query-group` option.  It is a flag and does not take any arguments.**

**Note: Only one query type may be done at a time.**

## Group name

Required: Always

Long form: `--group-name`

Short form: `-g`

Specifies the name of the group that you want to work with.

## Write to file

Long form: `--write-to-file`

This is a flag to indicate that the query information should be written to a file in addition to being displayed in the
terminal.  The name of the output file is fixed and will be displayed in the terminal.

## Get group CA

Long form: `--get-group-ca`

This is a flag to indicate that the group's CA information should be retrieved.

## List subscriptions

Long form: `--list-subscriptions`

This is a flag to indicate that the group's subscriptions should be listed.

## List devices

Long form: `--list-devices`

This is a flag to indicate that the group's devices should be listed.

## List functions

Long form: `--list-functions`

This is a flag to indicate that the group's functions should be listed.

## Download logs

Long form: `--download-logs`

This is a flag to indicate that the group's logs from CloudWatch should be downloaded into the directory `logs/GROUP_NAME`.

## Watch logs

Long form: `--watch-logs`

This is a flag to indicate that the group's logs from CloudWatch should "tailed" like `tail -F` in Linux.

## Diagnose

Long form: `--diagnose`

This is a flag to indicate that the group's logs from CloudWatch should be checked for common issues.
