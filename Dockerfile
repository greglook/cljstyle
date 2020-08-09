FROM ubuntu:20.04
COPY . /src
RUN apt-get update \
    && apt-get -y install build-essential libz-dev curl leiningen \
    && cd /src \
    && make package \
    && chmod +x cljstyle

FROM ubuntu:20.04
COPY --from=0 /src/cljstyle /usr/local/bin/cljstyle
ENTRYPOINT cljstyle
