# Build file for cljstyle

.PHONY: all clean lint check set-version graal package

version := $(shell grep defproject project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
uberjar_path := target/uberjar/cljstyle.jar

# Graal settings
GRAAL_ROOT ?= /tmp/graal
graal_version := 20.2.0
graal_archive := graalvm-ce-java11-$(platform)-amd64-$(graal_version).tar.gz
graal_home := $(GRAAL_ROOT)/graalvm-ce-java11-$(graal_version)

# Rewrite darwin as a more recognizable OS
ifeq ($(platform),darwin)
platform := macos
graal_home := $(graal_home)/Contents/Home
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
	    -e 's|mvxcvi/cljstyle ".*"|mvxcvi/cljstyle "$(new-version)"|' \
	    -e 's|CLJSTYLE_VERSION: .*|CLJSTYLE_VERSION: $(new-version)|' \
	    -e 's|cljstyle.git", :tag ".*"}|cljstyle.git", :tag "$(new-version)"}|' \
	    doc/integrations.md
	@echo "$(new-version)" > CLJSTYLE_RELEASED_VERSION

$(uberjar_path): project.clj $(shell find resources -type f) $(shell find src -type f)
	lein uberjar

$(GRAAL_ROOT)/fetch/$(graal_archive):
	@mkdir -p $(GRAAL_ROOT)/fetch
	curl --location --output $@ https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-$(graal_version)/$(graal_archive)

$(graal_home): $(GRAAL_ROOT)/fetch/$(graal_archive)
	tar -xz -C $(GRAAL_ROOT) -f $<

$(graal_home)/bin/native-image: $(graal_home)
	$(graal_home)/bin/gu install native-image

graal: $(graal_home)/bin/native-image

cljstyle: $(uberjar_path) $(graal_home)/bin/native-image
	$(graal_home)/bin/native-image \
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
