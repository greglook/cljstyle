Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

This is a significant release which revamps the tooling entirely. Instead of a
Leiningen plugin, the tool is now a stand-alone native binary compiled with
Graal.

### Added
- New `cljfmt.config` namespace for specs and working with configuration files.
- Configuration can now be loaded and merged from multiple `.cljfjmt` files on
  disk.
- Command logic is now captured in a CLI tool which is compiled with Graal.

### Changed
- Combined default indent rules into a single `cljfmt/indents.clj` resource.
- Files are now processed using a `ForkJoinPool` to efficiently utilize
  processor cores.

### Removed
- Removed `lein-cljfmt` project.
- Removed Clojurescript cross-compiling support.

## [0.7.0] - 2019-05-18

First fork release. Rewrote most of the code and added a bunch of new
functionality.

## 0.5.6 - 2016-09-29

Legacy project release.

[Unreleased]: https://github.com/greglook/cljfmt/compare/0.7.0...HEAD
[0.7.0]: https://github.com/greglook/cljfmt/compare/0.5.6...0.7.0
