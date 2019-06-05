FROM ubuntu:18.04

# Update package manager
RUN apt-get update -y && apt-get upgrade -y && \
    apt-get -y clean

# Install wget so we can fetch Device Tester
RUN apt-get install -y wget libdigest-sha-perl && \
    apt-get -y clean

# Fetch and validate Device Tester
RUN cd / && \
    echo "7df84a257824cea25e91bd910878a0a519bbaa04  devicetester_greengrass_linux_1.3.1.zip" > /devicetester_greengrass_linux_1.3.1.zip.sha && \
    wget --referer=https://aws.amazon.com/greengrass/device-tester/ https://d232ctwt5kahio.cloudfront.net/greengrass/devicetester_greengrass_linux_1.3.1.zip && \
    shasum -c /devicetester_greengrass_linux_1.3.1.zip.sha

# Install JDK so Java functions can be built
RUN apt-get install -y openjdk-8-jdk-headless && \
    apt-get -y clean

# Install pip so Python functions can be built
RUN apt-get install -y python-pip && \
    apt-get -y clean

# Install NodeJS and npm so Node functions can be built
RUN apt-get install -y npm && \
    apt-get -y clean

# Install latest version of Gradle with sources to speed up Java builds. Lambda function developers need sources but we don't so we clear them out.
#   If we just install the version without sources though it will still attempt to download the distribution each time since it is named differently.
#   Therefore we download the sources distribution but clean out the components we don't need.
RUN apt-get install -y gradle && \
    mkdir temp && \
    cd temp && \
    gradle init && \
    gradle wrapper --gradle-version 5.4.1 --distribution-type all && \
    ./gradlew tasks && \
    cd .. && \
    rm -rf temp && \
    apt-get --purge -y remove gradle && \
    apt-get --purge -y autoremove && \
    apt-get -y clean && \
    rm -rf /root/.gradle/wrapper/dists/*/*/*/src \
           /root/.gradle/wrapper/dists/*/*/*/samples \
           /root/.gradle/wrapper/dists/*/*/*/media \
           /root/.gradle/wrapper/dists/*/*/*.zip \
           /root/.gradle/wrapper/dists/*/*/*/docs

COPY AwsGreengrassProvisioner.jar AwsGreengrassProvisioner.jar

ENTRYPOINT ["java", "-jar", "AwsGreengrassProvisioner.jar"]
