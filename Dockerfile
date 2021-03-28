FROM clojure:lein-2.9.1

# Install essential tooling
RUN apt update
RUN apt install --no-install-recommends -yy curl unzip build-essential zlib1g-dev

# Download and configure GraalVM
WORKDIR /opt
ARG GRAAL_VERSION="21.0.0"
ENV GRAAL_HOME="/opt/graalvm-ce-java11-$GRAAL_VERSION"
RUN curl -sLO https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-$GRAAL_VERSION/graalvm-ce-java11-linux-amd64-$GRAAL_VERSION.tar.gz
RUN tar -xzf graalvm-ce-java11-linux-amd64-$GRAAL_VERSION.tar.gz
RUN $GRAAL_HOME/bin/gu install native-image

ENV JAVA_HOME="$GRAAL_HOME/bin"
ENV PATH="$JAVA_HOME:$PATH"

WORKDIR /opt/cljstyle

# Prefetch project dependencies
COPY project.clj .
RUN lein deps

# Build uberjar
COPY . .
RUN ./script/uberjar

# Build native-image
ARG GRAAL_XMX="4500m"
ARG GRAAL_STATIC="false"
ENV GRAAL_XMX="$GRAAL_XMX"
ENV GRAAL_STATIC="$GRAAL_STATIC"
RUN ./script/compile

# Install tool
RUN mkdir -p /usr/local/bin && cp cljstyle /usr/local/bin/cljstyle
CMD ["cljstyle"]
