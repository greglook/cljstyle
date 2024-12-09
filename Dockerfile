FROM clojure:tools-deps

# Install essential tooling
RUN apt update
RUN apt install --no-install-recommends -yy curl unzip build-essential zlib1g-dev

# Download and configure GraalVM
WORKDIR /opt
ARG GRAAL_VERSION="23.0.1"
ENV GRAAL_HOME="/opt/graalvm"
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
ENV JAVA_HOME="$GRAAL_HOME"
ENV PATH="$JAVA_HOME/bin:$PATH"

# Prefetch project dependencies
COPY deps.edn build.clj .
RUN clojure -T:build fetch-deps

# Build uberjar
COPY . .
RUN ./bin/build graal-uberjar

# Setup musl compiler if building a static binary
ARG GRAAL_STATIC="false"
RUN if [ "$GRAAL_STATIC" = true ]; then ./bin/build setup-musl; fi

# Build native-image
RUN ./bin/build native-image :graal-static $GRAAL_STATIC

# Install tool
RUN mkdir -p /usr/local/bin && cp target/graal/cljstyle /usr/local/bin/cljstyle
CMD ["cljstyle"]
