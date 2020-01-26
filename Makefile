# Build file for cljstyle

.PHONY: all clean lint package

version := $(shell grep defproject core/project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
release_name = cljstyle_$(version)_$(platform)

lib_install_path := $(HOME)/.m2/repository/mvxcvi/cljstyle/$(version)/cljstyle-$(version).jar
tool_uberjar_path := tool/target/uberjar/cljstyle.jar

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
	rm -rf dist cljstyle core/target tool/target

lint:
	clj-kondo --lint core/src core/test tool/src

$(lib_install_path): core/project.clj core/src/**/* core/resources/**/*
	cd core; lein install

$(tool_uberjar_path): tool/project.clj tool/src/**/* $(lib_install_path)
	cd tool; lein uberjar

cljstyle: $(tool_uberjar_path)
	$(GRAAL_HOME)/bin/native-image \
	    --no-fallback \
	    --allow-incomplete-classpath \
	    --report-unsupported-elements-at-runtime \
	    --initialize-at-build-time \
	    -J-Xms3G -J-Xmx3G \
	    --no-server \
	    -jar $<

dist/$(release_name).tar.gz: cljstyle
	@mkdir -p dist
	tar -cvzf $@ $^

package: dist/$(release_name).tar.gz
