# How It Works

## Building functions

GGP builds Python, Node, Java, and native functions.  Specifically it builds an AWS Lambda Deployment Package.

It builds the deployment package similarly to how a user would do it by hand.  It performs the following steps:

- (Java) Run `./gradlew build` on the code to create a JAR/ZIP
- (Python) Retrieve the dependencies in the dependency list in requirements.txt using pip
- (Node) Retrieve the dependencies in the dependency list in package.json using npm
- (Python and Node) Package the code and dependencies in a ZIP file
- (Native) Run `./build.sh` in the function directory and look for it to create a ZIP file with the same name as the directory to indicate success
- Upload the ZIP to AWS Lambda
- Publish a version of the function in AWS Lambda
- Create an alias to the new version of the function in AWS Lambda

# More?

Want to know how a different part of the system works?  Open a Github issue and ask for us to add a new section here.
