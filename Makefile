# Build file for cljstyle

.PHONY: all clean lint check uberjar set-version graal package

version := $(shell grep defproject project.clj | cut -d ' ' -f 3 | tr -d \")
platform := $(shell uname -s | tr '[:upper:]' '[:lower:]')
uberjar_path := target/uberjar/cljstyle.jar

# Graal settings
GRAAL_ROOT ?= /tmp/graal
GRAAL_VERSION ?= 22.1.0
GRAAL_HOME ?= $(GRAAL_ROOT)/graalvm-ce-java11-$(GRAAL_VERSION)
graal_archive := graalvm-ce-java11-$(platform)-amd64-$(GRAAL_VERSION).tar.gz

# Rewrite darwin as a more recognizable OS
ifeq ($(platform),darwin)
platform := macos
GRAAL_HOME := $(GRAAL_HOME)/Contents/Home
endif


all: cljstyle


### Utilities ###

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
	@echo "$(new-version)" > VERSION.txt


### GraalVM Install ###

$(GRAAL_ROOT)/fetch/$(graal_archive):
	@mkdir -p $(GRAAL_ROOT)/fetch
	curl --location --output $@ https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-$(GRAAL_VERSION)/$(graal_archive)

$(GRAAL_HOME): $(GRAAL_ROOT)/fetch/$(graal_archive)
	tar -xz -C $(GRAAL_ROOT) -f $<

$(GRAAL_HOME)/bin/native-image: $(GRAAL_HOME)
	$(GRAAL_HOME)/bin/gu install native-image

graal: $(GRAAL_HOME)/bin/native-image


### Local Build ###

SRC := project.clj $(shell find resources -type f) $(shell find src -type f)

$(uberjar_path): $(SRC)
	script/uberjar

uberjar: $(uberjar_path)

cljstyle: $(uberjar_path) $(GRAAL_HOME)/bin/native-image
	GRAAL_HOME=$(GRAAL_HOME) script/compile


#### Distribution Packaging ###

release_jar := cljstyle-$(version).jar
release_macos_tgz := cljstyle_$(version)_macos.tar.gz
release_macos_zip := cljstyle_$(version)_macos.zip
release_linux_tgz := cljstyle_$(version)_linux.tar.gz
release_linux_zip := cljstyle_$(version)_linux.zip
release_linux_static_zip := cljstyle_$(version)_linux_static.zip

# Uberjar
dist/$(release_jar): $(uberjar_path)
	@mkdir -p dist
	cp $< $@

# Mac OS X
ifeq ($(platform),macos)
dist/$(release_macos_tgz): cljstyle
	@mkdir -p dist
	tar -cvzf $@ $^

dist/$(release_macos_zip): cljstyle
	@mkdir -p dist
	zip $@ $^
endif

# Linux
target/package/linux/cljstyle: Dockerfile $(SRC)
	script/docker-build --output $@

dist/$(release_linux_tgz): target/package/linux/cljstyle
	@mkdir -p dist
	tar -cvzf $@ -C $(dir $<) $(notdir $<)

dist/$(release_linux_zip): target/package/linux/cljstyle
	@mkdir -p dist
	cd $(dir $<); zip $(abspath $@) $(notdir $<)

# Linux (static)
target/package/linux-static/cljstyle: Dockerfile $(SRC)
	script/docker-build --static --output $@

dist/$(release_linux_static_zip): target/package/linux-static/cljstyle
	@mkdir -p dist
	cd $(dir $<); zip $(abspath $@) $(notdir $<)

# Metapackage
ifeq ($(platform),macos)
package: dist/$(release_jar) dist/$(release_macos_tgz) dist/$(release_macos_zip) dist/$(release_linux_tgz) dist/$(release_linux_zip) dist/$(release_linux_static_zip)
else
package: dist/$(release_jar) dist/$(release_linux_tgz) dist/$(release_linux_zip) dist/$(release_linux_static_zip)
endif
