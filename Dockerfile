FROM ubuntu:20.04
RUN apt-get update \
    && apt-get -y install curl \
    && curl -LO https://github.com/greglook/cljstyle/releases/download/0.13.0/cljstyle_0.13.0_linux.tar.gz \
    && tar -zxvf cljstyle_0.13.0_linux.tar.gz \
    && mv cljstyle /usr/local/bin
ENTRYPOINT cljstyle
