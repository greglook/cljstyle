# Build file for cljfmt

.PHONY: setup clean uberjar package

all: package

uberjar_path := target/uberjar/cljfmt.jar
version := $(shell grep defproject project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
release_name := cljfmt_$(version)_$(platform)

ifndef GRAAL_PATH
$(error GRAAL_PATH is not set)
endif

setup:
	lein deps

clean:
	rm -rf target dist cljfmt

$(uberjar_path): src/**/* svm/java/**/*
	lein with-profile +svm uberjar

uberjar: $(uberjar_path)

cljfmt: reflection-config := svm/reflection-config.json
cljfmt: $(uberjar_path) $(reflection-config)
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
