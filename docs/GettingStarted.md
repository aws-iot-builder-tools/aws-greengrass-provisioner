# Getting Started

**Remember: Any references to `GGP` in commands in this documentation must be replaced with the appropriate command for
your environment (e.g. `./deploy.sh`, `java -jar ...`, etc.)**

## Before you start

GGP runs on a Cloud9 instance/desktop/laptop/development system.  In most cases you do not want to run it where you'll
be running Greengrass unless you are doing development on the same system.  GGP creates a bootstrap script that you copy
to your Pi/EC2 instance/gateway.

You must have AWS credentials configured on your system for GGP to work.  It uses the credentials and configuration in
the `$HOME/.aws` directory just like the AWS CLI.  The `deploy.sh` script attempts to get these credentials via the AWS
CLI's `aws configure ...` commands so it won't work if the AWS CLI isn't configured.  This also means that it will use
your default region.  If you want to override this behavior you need to set the appropriate
[environment variables for the default credential provider chain](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default).

**Docker on EC2 users:** If you run GGP in Docker on an EC2 instance it will not pick up your instance credentials since
it tries to get them from the AWS CLI as mentioned above.  We are looking to improve this in the future.

## If you are using GGP in a Docker container

Python, Java, and NodeJS are pre-installed on the Docker container.  You do not need them on the host operating system.

## If you are not using the provisioner in a Docker container

**Python users:** If you are deploying Python functions you must have Python and pip already installed on your host system.

**Java users:** If you are deploying Java functions you must have Java already installed on your host system.

**NodeJS users:** If you are deploying NodeJS functions you must have NodeJS and npm already installed on your host system.

## Do a simple deployment first

**Note:** You will need the deployments and functions directories from the
[AWS Samples Greengrass Lambda Functions repo](https://github.com/aws-samples/aws-greengrass-lambda-functions).

**Remember: Any references to `GGP` in commands in this documentation must be replaced with the appropriate command for
your environment (e.g. `./deploy.sh`, `java -jar ...`, etc.)**

Using a Raspberry Pi?  Use this command:

```bash
GGP -a ARM32 -g mytestgroup -d deployments/python-hello-world.conf --script
```

Using EC2?  Use this command:

```bash
GGP -a X86_64 -g mytestgroup -d deployments/python-hello-world.conf --script
```

After some churning a new file called `gg.mytestgroup.sh` will be created.  Copy that file to the system that you'll be using
as your Greengrass Core.

ssh to your system and run `./gg.mytestgroup.sh`.  It will ask you three questions that you should respond `y` to:

* `Install Greengrass?` - This unpacks Greengrass and puts all of the files into the `/greengrass` directory
* `Start Greengrass?` - This runs `./start.sh` to start Greengrass when the installation is complete
* `Update dependencies?` - This installs all of the dependencies for Greengrass

If you run it for the first time and you get the message `You must reboot and re-run this installer` run
`sudo reboot`.  Wait for the device to restart, ssh back in, and run the installer script again.  Answer `y` to
all three questions again.

A first time installation on a fresh Pi will take about 10 minutes depending on your network connection. A fresh EC2
instance should take about 3 minutes.

When the script is finished and Greengrass starts it will pull down your deployment of the Hello World function
in Python.  Your console will be monitoring the Greengrass logs at this point.  You can CTRL-C out of it if you
need to get back to the system.  You can start the monitoring again by running `./monitor.sh`.

After a successful deployment the last four lines you should expect to see in the console will look like this:

```
[2018-01-17T21:14:01.318Z][INFO]-Trying to subscribe to topic $aws/things/mytestgroup_Core-gda/shadow/update/delta
[2018-01-17T21:14:01.355Z][INFO]-Subscribed to : $aws/things/mytestgroup_Core-gda/shadow/update/delta
[2018-01-17T21:14:01.355Z][INFO]-Trying to subscribe to topic $aws/things/mytestgroup_Core-gda/shadow/get/accepted
[2018-01-17T21:14:01.422Z][INFO]-Subscribed to : $aws/things/mytestgroup_Core-gda/shadow/get/accepted
```

At this point you can to go the AWS IoT console on AWS, subscribe to the `hello/world` topic and you should see
messages showing up every 5 seconds like this:

```
Hello world! Sent from Greengrass Core running on platform: Linux-4.9.30-v7+-armv7l-with-debian-9.1 c9855443-944a-4184-992a-b810438c0273 mytestgroup_Core arn:aws:iot:us-east-1:541589084637:thing/mytestgroup_Core
```

### Modify the code and redeploy

Modify the code in the `functions/HelloWorldPython/HelloWorldPython.py` file.  Change the message payloads or
the timer interval.  Re-run the provisioning process on your laptop with the same Java command-line you used
above.

The Greengrass Core will now update automatically and you should see your new code running in less than 2 minutes.

### Need to restart?

If your system uses systemd this script attempts to set up Greengrass to restart when you reboot.  If you want to start
Greengrass again manually DO NOT re-run the install script.  Instead, just run `./start.sh` on your system and
Greengrass will start again if necessary.

### Ready for something more exciting?

Try some of the other deployments!  Remember you need the appropriate languages installed on your local system to build
Lambda functions.  If you don't have them installed (Python/pip, NodeJS/npm, Java/Gradle) install them or use the Docker
hub image.

The deployment configurations tell GGP which functions you want to deploy.  Each function should have a README.md
explaining what it is for. Each function should also have a `Java`, `Python`, or `Node` suffix so you know which runtime
it uses.

### Need help?

File a Github issue in our repo if you think you've found a bug.  We'll get back to you as soon as we can.

