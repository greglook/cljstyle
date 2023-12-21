FROM --platform=linux/amd64 clojure:tools-deps

# Install essential tooling
RUN apt update
RUN apt install --no-install-recommends -yy curl unzip build-essential zlib1g-dev

# Download and configure GraalVM
WORKDIR /opt
ARG GRAAL_VERSION="21.0.1"
ENV GRAAL_HOME="/opt/graalvm"
# TODO: do this in one step to save image layers?
RUN \
    curl \
        --silent \
        --location \
        --output /tmp/graalvm-ce.tar.gz \
        https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAAL_VERSION}/graalvm-community-jdk-${GRAAL_VERSION}_linux-x64_bin.tar.gz \
    && tar -xzf /tmp/graalvm-ce.tar.gz \
    && mv /opt/graalvm-community-* $GRAAL_HOME \
    && rm /tmp/graalvm-ce.tar.gz

WORKDIR /opt/cljstyle
ENV JAVA_HOME="$GRAAL_HOME/bin"
ENV PATH="$JAVA_HOME:$PATH"

# Prefetch project dependencies
COPY deps.edn build.clj .
RUN clojure -T:build fetch-deps

# Build uberjar
COPY . .
RUN ./bin/build graal-uberjar

# Build native-image
ARG GRAAL_STATIC="false"
RUN ./bin/build native-image :graal-static $GRAAL_STATIC

# Install tool
RUN mkdir -p /usr/local/bin && cp target/graal/cljstyle /usr/local/bin/cljstyle
CMD ["cljstyle"]
