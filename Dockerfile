FROM frolvlad/alpine-glibc:alpine-3.12
COPY project.clj .
RUN apk add --no-cache wget tar \
    && version=$(cat project.clj | grep defproject | cut -d' ' -f3 | cut -d'"' -f2) \
    && wget https://github.com/greglook/cljstyle/releases/download/$version/cljstyle_${version}_linux.tar.gz \
    && tar -zxvf cljstyle_${version}_linux.tar.gz

FROM frolvlad/alpine-glibc:alpine-3.12
COPY --from=0 cljstyle /usr/local/bin
ENTRYPOINT cljstyle
