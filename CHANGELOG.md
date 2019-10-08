Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

### Fixed
- The cljfmt codebase now adheres to its own style rules.
- Private functions defined with `defn-` are no longer ignored by function
  rules.
  [#17](//github.com/greglook/cljfmt/issues/17)


## [0.8.2] - 2019-08-20

### Fixed
- Configuration file paths are tracked more accurately for reporting.
- Namespace metadata is properly retained.
  [#11](//github.com/greglook/cljfmt/issues/11)
- Vector import specs no longer cause `UnsupportedOperationException`.
  [#12](//github.com/greglook/cljfmt/issues/12)
- Namespaced maps are indented correctly and allow for abutting same-line
  namespace tags.
  [#6](https://github.com/greglook/cljfmt/issues/6)
  [#7](https://github.com/greglook/cljfmt/pull/7)

### Changed
- Many new tests around configuration file loading and merge behavior.
- Split test namespaces up into more focused areas.


## [0.8.1] - 2019-08-01

### Added
- In addition to the file status counts, task stats now include the total number
  of files, the elapsed time, and the number of lines present in the diff (if
  applicable).
- A new `--stats FILE` option allows writing the task statistics to a file after
  processing is complete. The file name may end in `.edn` or `.tsv` to control
  the output format.

### Changed
- Most task code moved into core `cljfmt` library so it can be used by plugins
  if desired.
- Retain full list of config paths as metadata for debugging.

### Fixed
- Search roots are canonicalized now to handle `.` and `..` properly.
- Searching up the directory hierarchy for parent config files no longer
  double-loads configuration in the current directory.
- Print options now correctly propagate to subtasks, so passing `--verbose` and
  `--no-color` will work on the tool output.
- Using the `config` task on a file will produce the correct config list now.


## [0.8.0] - 2019-07-27

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

[Unreleased]: https://github.com/greglook/cljfmt/compare/0.8.2...HEAD
[0.8.2]: https://github.com/greglook/cljfmt/compare/0.8.1...0.8.2
[0.8.1]: https://github.com/greglook/cljfmt/compare/0.8.0...0.8.1
[0.8.0]: https://github.com/greglook/cljfmt/compare/0.7.0...0.8.0
[0.7.0]: https://github.com/greglook/cljfmt/compare/0.5.6...0.7.0
