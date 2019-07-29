# Build file for cljfmt

.PHONY: all clean setup package

uberjar_path := target/uberjar/cljfmt.jar
version := $(shell grep defproject project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
release_name := cljfmt_$(version)_$(platform)

ifndef GRAAL_PATH
$(error GRAAL_PATH is not set)
endif

all: package

clean:
	rm -rf target dist cljfmt

setup:
	lein deps

$(uberjar_path): project.clj src/**/*
	lein uberjar

cljfmt: $(uberjar_path)
	$(GRAAL_PATH)/bin/native-image \
	    --no-fallback \
	    --allow-incomplete-classpath \
	    --report-unsupported-elements-at-runtime \
	    --initialize-at-build-time \
	    -J-Xms3G -J-Xmx3G \
	    --no-server \
	    -jar $<

dist/$(release_name).tar.gz: cljfmt
	@mkdir -p dist
	tar -cvzf $@ $^

package: dist/$(release_name).tar.gz
