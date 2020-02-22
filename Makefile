# Build file for cljstyle

.PHONY: all clean lint check package

version := $(shell grep defproject project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
uberjar_path := target/uberjar/cljstyle.jar
release_jar := cljstyle-$(version).jar
release_tgz := cljstyle_$(version)_$(platform).tar.gz

# Rewrite darwin as a more recognizable OS
ifeq ($(platform),darwin)
platform := macos
endif

# Ensure Graal is available
ifndef GRAAL_HOME
$(error GRAAL_HOME is not set)
endif

all: cljstyle

clean:
	rm -rf dist cljstyle target

lint:
	clj-kondo --lint src test
	lein yagni

check:
	lein check

$(uberjar_path): project.clj resources/**/* src/**/*
	lein uberjar

cljstyle: $(uberjar_path)
	$(GRAAL_HOME)/bin/native-image \
	    --no-fallback \
	    --allow-incomplete-classpath \
	    --report-unsupported-elements-at-runtime \
	    --initialize-at-build-time \
	    -J-Xms3G -J-Xmx3G \
	    --no-server \
	    -jar $<

dist/$(release_tgz): cljstyle
	@mkdir -p dist
	tar -cvzf $@ $^

dist/$(release_jar): $(uberjar_path)
	@mkdir -p dist
	cp $< $@

package: dist/$(release_jar) dist/$(release_tgz)
