#!/usr/bin/env bash

set -e

STACK_NAME=$1
GROUP_NAME=$2

if [ -z "$STACK_NAME" ];
then
  echo "Stack name must be specified"
  exit 1
fi

if [ -z "$GROUP_NAME" ];
then
  GROUP_NAME=$(uuidgen | sed s/^/A/ )
  echo "Generating random group name"
fi

echo "Group name $GROUP_NAME"

LAMBDA_FUNCTION=$(aws cloudformation describe-stack-resources --stack-name $STACK_NAME --query 'StackResources[?LogicalResourceId==`ProvisionerLambdaFunction`].PhysicalResourceId' --output text)
CORE_ROLE_NAME=$(aws cloudformation describe-stack-resources --stack-name $STACK_NAME --query 'StackResources[?LogicalResourceId==`GreengrassCoreRole`].PhysicalResourceId' --output text)
CORE_POLICY_NAME=$(aws cloudformation describe-stack-resources --stack-name $STACK_NAME --query 'StackResources[?LogicalResourceId==`MinimalGreengrassCoreIoTPolicy`].PhysicalResourceId' --output text)

# Make sure the policy exists
aws iot get-policy --policy-name $CORE_POLICY_NAME > /dev/null

if [ ! -z "$CSR" ]; then
  CSR_DATA=$(cat $CSR | tr '\r\n' ' ')
  CSR=", \"csr\": \"$CSR_DATA\""
fi

if [ ! -z "$CERTIFICATE_ARN" ]; then
  CERTIFICATE_ARN=", \"certificateArn\": \"$CERTIFICATE_ARN\""
fi

PAYLOAD="{ \"groupName\": \"$GROUP_NAME\", \"coreRoleName\": \"$CORE_ROLE_NAME\", \"serviceRoleExists\": true, \"corePolicyName\": \"$CORE_POLICY_NAME\" $CSR $CERTIFICATE_ARN }"

time aws lambda invoke --function-name $LAMBDA_FUNCTION --invocation-type RequestResponse --payload "$PAYLOAD" $GROUP_NAME.outfile.txt
