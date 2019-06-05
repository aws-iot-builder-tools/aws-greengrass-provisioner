FROM openjdk:8u212-b04-jdk-stretch

COPY build.gradle /build/
COPY gradlew /build/
COPY gradle/wrapper/ /build/gradle/wrapper
WORKDIR /build/

# Run clean to at least fetch gradle
RUN ./gradlew clean

# Fetch dependencies
RUN ./gradlew resolveDependencies

# Fetch binaries
RUN ./gradlew downloadAll

COPY src /build/src
RUN ./gradlew build
