# How It Works

## Building functions

GGP builds Python, Node, and Java functions.  Specifically it builds an AWS Lambda Deployment Package.

It builds the deployment package similarly to how a user would do it by hand.  It performs the following steps:

- (Java) Run `./gradlew build` on the code to create a JAR/ZIP
- (Python) Retrieve the dependencies in the dependency list in function.conf using pip
- (Node) Retrieve the dependencies in the dependency list in function.conf using npm
- (Python and Node) Package the code and dependencies in a ZIP file
- Upload the ZIP to AWS Lambda
- Publish a version of the function in AWS Lambda
- Create an alias to the new version of the function in AWS Lambda

# More?

Want to know how a different part of the system works?  Open a Github issue and ask for us to add a new section here.
