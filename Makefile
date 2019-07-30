# Build file for cljfmt

.PHONY: all clean package

version := $(shell grep defproject core/project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
release_name := cljfmt_$(version)_$(platform)

lib_install_path := $$HOME/.m2/repository/mvxcvi/cljfmt/$(version)/cljfmt-$(version).jar
tool_uberjar_path := tool/target/uberjar/cljfmt.jar

ifndef GRAAL_HOME
$(error GRAAL_HOME is not set)
endif

all: cljfmt

clean:
	rm -rf dist cljfmt core/target tool/target

$(lib_install_path): core/project.clj core/src/**/* core/resources/**/*
	cd core; lein install

$(tool_uberjar_path): tool/project.clj tool/src/**/* $(lib_install_path)
	cd tool; lein uberjar

cljfmt: $(tool_uberjar_path)
	$(GRAAL_HOME)/bin/native-image \
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
