#!/usr/bin/env bash

set -e
set -x

if [ -z "$EVENT_TYPE" ]; then
  echo "No event type(Provision or Deploy) specified, can not continue"
  exit 1
fi

if [ -z "$LAMBDA_FUNCTION" ]; then
  if [ -z "$STACK_NAME" ]; then
    echo "Stack name [STACK_NAME] must be specified if the Lambda function name [LAMBDA_FUNCTION] is not specified so it can be looked up"
    exit 1
  fi

  LAMBDA_FUNCTION=$(aws cloudformation describe-stack-resources --stack-name $STACK_NAME --query 'StackResources[?LogicalResourceId==`ProvisionerPolymorphLambdaFunction`].PhysicalResourceId' --output text)

  if [ -z "$LAMBDA_FUNCTION" ]; then
    echo "Lambda function could not be found in the CloudFormation stack, can not continue"
    exit 1
  fi
fi

if [ -z "$CORE_ROLE_NAME" ]; then
  if [ -z "$STACK_NAME" ]; then
    echo "Stack name [STACK_NAME] must be specified if the core role name [CORE_ROLE_NAME] is not specified so it can be looked up"
    exit 1
  fi

  CORE_ROLE_NAME=$(aws cloudformation describe-stack-resources --stack-name $STACK_NAME --query 'StackResources[?LogicalResourceId==`GreengrassCoreRole`].PhysicalResourceId' --output text)
fi

if [ -z "$CORE_POLICY_NAME" ]; then
  if [ -z "$STACK_NAME" ]; then
    echo "Stack name [STACK_NAME] must be specified if the core policy name [CORE_POLICY_NAME] is not specified so it can be looked up"
    exit 1
  fi

  CORE_POLICY_NAME=$(aws cloudformation describe-stack-resources --stack-name $STACK_NAME --query 'StackResources[?LogicalResourceId==`MinimalGreengrassCoreIoTPolicy`].PhysicalResourceId' --output text)
fi

if [ -z "$GROUP_NAME" ]; then
  GROUP_NAME=$(uuidgen | sed s/^/A/)
  echo "No group name specified, generating random group name"
fi

if [ -z "$REGION" ]; then
  echo "Region not specified, using standard region provider for region selection"
else
  echo "Using region '$REGION'"
  AWS_DEFAULT_REGION=$REGION
fi

if [ -z "$ROLE_DATA" ]; then
  echo "Role data not specified, using standard credential provider for AWS credentials"
  CREDENTIALS=""
  CREDENTIALS_JSON=""
else
  command -v jq >/dev/null 2>&1 || {
    echo >&2 "jq is required to assume a role but it's not installed. Aborting."
    exit 1
  }

  if [[ $ROLE_DATA == arn:aws:iam::* ]]; then
    # Get temporary credentials from STS
    ROLE_ARN=$ROLE_DATA
    echo "Assuming role '$ROLE_ARN'"

    CREDENTIALS=$(aws sts assume-role --role-arn $ROLE_ARN --role-session-name ggp)
  else
    # Credentials specified as JSON already
    CREDENTIALS=$ROLE_DATA

    AWS_SESSION_TOKEN=$(jq --raw-output .credentials.sessionToken <(echo $CREDENTIALS))

    if [[ $AWS_SESSION_TOKEN == null ]]; then
      echo "IAM user credentials detected, getting temporary credentials via STS"

      # This is an IAM user's credentials, get temporary credentials so they can't be reused later
      CREDENTIALS=$(aws sts get-session-token)
    else
      echo "Using provided temporary credentials"
    fi
  fi

  AWS_ACCESS_KEY_ID=$(jq --raw-output .Credentials.AccessKeyId <(echo $CREDENTIALS))
  AWS_SECRET_ACCESS_KEY=$(jq --raw-output .Credentials.SecretAccessKey <(echo $CREDENTIALS))
  AWS_SESSION_TOKEN=$(jq --raw-output .Credentials.SessionToken <(echo $CREDENTIALS))

  CREDENTIALS_JSON=", \"accessKeyId\": \"$AWS_ACCESS_KEY_ID\", \"secretAccessKey\": \"$AWS_SECRET_ACCESS_KEY\", \"sessionToken\": \"$AWS_SESSION_TOKEN\""

  if [ -n "$AWS_DEFAULT_REGION" ];
  then
    DEFAULT_REGION_TEXT="AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION"
    AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION
  else
    # Use a blank string instead of the region information if the user hasn't specified a region
    DEFAULT_REGION_TEXT=""
  fi

  CREDENTIALS="$DEFAULT_REGION_TEXT AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN=$AWS_SESSION_TOKEN"
fi

echo "Group name $GROUP_NAME"

# Make sure the policy exists (bash -c $CREDENTIALS will check in the target account if the target account is different with environment variables)
bash -c "$CREDENTIALS aws iot get-policy --policy-name $CORE_POLICY_NAME >/dev/null"

if [ -n "$CSR" ]; then
  CSR_DATA=$(cat $CSR | tr '\r\n' ' ')
  CSR=", \"csr\": \"$CSR_DATA\""
fi

if [ -n "$CERTIFICATE_ARN" ]; then
  CERTIFICATE_ARN=", \"certificateArn\": \"$CERTIFICATE_ARN\""
fi

PAYLOAD="{ \"eventType\": \"$EVENT_TYPE\", \"groupName\": \"$GROUP_NAME\", \"coreRoleName\": \"$CORE_ROLE_NAME\", \"serviceRoleExists\": true, \"corePolicyName\": \"$CORE_POLICY_NAME\" $CSR $CERTIFICATE_ARN $CREDENTIALS_JSON }"

if [[ "$EVENT_TYPE" == "Provision" ]]; then
  time aws lambda invoke --function-name $LAMBDA_FUNCTION --invocation-type RequestResponse --payload "$PAYLOAD" --cli-binary-format raw-in-base64-out $GROUP_NAME.outfile.txt
else
  time aws lambda invoke --function-name $LAMBDA_FUNCTION --invocation-type RequestResponse --payload file://deploy-payload.json --cli-binary-format raw-in-base64-out $GROUP_NAME.outfile.txt  
fi


