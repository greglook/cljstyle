# Build file for cljstyle

.PHONY: all clean lint check set-version package

version := $(shell grep defproject project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
uberjar_path := target/uberjar/cljstyle.jar

# Rewrite darwin as a more recognizable OS
ifeq ($(platform),darwin)
platform := macos
endif

release_jar := cljstyle-$(version).jar
release_tgz := cljstyle_$(version)_$(platform).tar.gz

all: cljstyle

clean:
	rm -rf dist cljstyle target

lint:
	clj-kondo --lint src test
	lein yagni

check:
	lein check

new-version=$(version)
set-version:
	@echo "Setting project and doc version to $(new-version)"
	@sed -i '' \
	    -e 's|^(defproject mvxcvi/cljstyle ".*"|(defproject mvxcvi/cljstyle "$(new-version)"|' \
	    project.clj
	@sed -i '' \
	    -e 's|CLJSTYLE_VERSION: .*|CLJSTYLE_VERSION: $(new-version)|' \
	    -e 's|{:git/url "https://github.com/greglook/cljstyle.git", :tag ".*"}|{:git/url "https://github.com/greglook/cljstyle.git", :tag "$(new-version)"}|' \
	    doc/integrations.md

$(uberjar_path): project.clj resources/**/* src/**/*
	lein uberjar

$(GRAAL_HOME)/bin/native-image:
	ifndef GRAAL_HOME
	$(error GRAAL_HOME is not set)
	endif
	$(GRAAL_HOME)/bin/gu install native-image

cljstyle: $(uberjar_path) $(GRAAL_HOME)/bin/native-image
	$(GRAAL_HOME)/bin/native-image \
	    --no-fallback \
	    --allow-incomplete-classpath \
	    --report-unsupported-elements-at-runtime \
	    --initialize-at-build-time \
	    -J-Xms3G -J-Xmx3G \
	    -J-Dclojure.compiler.direct-linking=true \
	    -J-Dclojure.spec.skip-macros=true \
	    --no-server \
	    -jar $<

dist/$(release_tgz): cljstyle
	@mkdir -p dist
	tar -cvzf $@ $^

dist/$(release_jar): $(uberjar_path)
	@mkdir -p dist
	cp $< $@

package: dist/$(release_jar) dist/$(release_tgz)
