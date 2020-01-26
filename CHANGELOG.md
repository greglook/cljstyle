Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

### Added
- The tool accepts a `--report` option which will cause the execution stats to
  be printed at the end of a run.
- Processing errors are now rethrown with more information about the specific
  function that failed and the position in the file which was problematic.
  [#29](//github.com/greglook/cljstyle/issues/29)

### Fixed
- Many performance optimizations across the formatting rules.


## [0.11.1] - 2020-01-22

### Added
- Added EPL file to repo to clarify licensing.

### Changed
- Modified the type formatting rules after feedback from users. This relaxes
  the `reify` rules slightly and uses two-blank-lines between inline method
  definitions.


## [0.11.0] - 2020-01-21

### Added
- Var definitions (using `def`) have line-break rules, constrolled by
  `:line-break-vars?`.
- Type definition forms `deftype`, `defrecord`, `defprotocol`, `reify`, and
  `proxy` have formatting rules, controlled by `:reformat-types?`.

### Fixed
- `:list-indent-size` now applies to `ns` forms as well.
  [#25](//github.com/greglook/cljstyle/pull/25)
- `defmacro` is now subject to function line-breaking rules.
- Fixed some cases where newlines would not be collapsed by line-break rules.
- A bug previously caused any list in a vector in a `letfn` form to be treated
  like a function definition.
- A bug caused `case` group lists beginning with a numeric value to throw an
  error when formatting the file.


## [0.10.1] - 2020-01-04

### Fixed
- The `pipe` command will respect configuration directly in the process working
  directory. Previously, it only considered parent directories.


## [0.10.0] - 2019-12-25

### Added
- The `:list-indent-size` configuration option allows the default indentation
  amount for lists to be adjusted.
  [#8](//github.com/greglook/cljstyle/issues/8)
  [#21](//github.com/greglook/cljstyle/pull/21)
- The `pipe` command reads Clojure code from stdin and writes the reformatted
  code to stdout.
  [#22](//github.com/greglook/cljstyle/pull/22)

### Fixed
- Clojurescript files using shadow-cljs style strings in libspecs for external
  JS libs will now work.
  [#20](//github.com/greglook/cljstyle/issues/20)
  [#23](//github.com/greglook/cljstyle/pull/23)


## [0.9.0] - 2019-10-13

The biggest change in this release is renaming the project to `cljstyle` to
better differentiate it from the original `cljfmt`.

### Fixed
- Reader conditional macros in namespace forms are correctly handled at the top
  level, inside `:require` type forms, and inside libspecs. Some other cases
  (such as conditionals inside `:import`) may still cause errors.
  [#5](//github.com/greglook/cljstyle/issues/5)
- Don't swallow comments on import package groups.
  [#10](//github.com/greglook/cljstyle/issues/10)
- Preserve commas when removing surrounding whitespace.
  [#14](//github.com/greglook/cljstyle/issues/14)
- Don't apply function line-breaking rules to forms within a syntax-quote.
- Anonymous functions with multiple arity forms are line-broken properly.

### Changed
- Imported classes under the `:single-import-break-width` threshold which were
  already in a package group will not be forced into a qualified class symbol.

### Added
- The `:require-eof-newline?` configuration option will ensure that all source
  files end with a newline character.
  [#15](//github.com/greglook/cljstyle/issues/15)
- Completion logic for zshell in [`completion.zsh`](util/completion.zsh).


## [0.8.3] - 2019-10-08

### Fixed
- The project codebase now adheres to its own style rules.
- Private functions defined with `defn-` are no longer ignored by function
  rules.
  [#17](//github.com/greglook/cljstyle/issues/17)


## [0.8.2] - 2019-08-20

### Fixed
- Configuration file paths are tracked more accurately for reporting.
- Namespace metadata is properly retained.
  [#11](//github.com/greglook/cljstyle/issues/11)
- Vector import specs no longer cause `UnsupportedOperationException`.
  [#12](//github.com/greglook/cljstyle/issues/12)
- Namespaced maps are indented correctly and allow for abutting same-line
  namespace tags.
  [#6](//github.com/greglook/cljstyle/issues/6)
  [#7](//github.com/greglook/cljstyle/pull/7)

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


## [0.5.6] - 2016-09-29

Legacy project release.


[Unreleased]: https://github.com/greglook/cljstyle/compare/0.11.1...HEAD
[0.11.1]: https://github.com/greglook/cljstyle/compare/0.11.0...0.11.1
[0.11.0]: https://github.com/greglook/cljstyle/compare/0.10.1...0.11.0
[0.10.1]: https://github.com/greglook/cljstyle/compare/0.10.0...0.10.1
[0.10.0]: https://github.com/greglook/cljstyle/compare/0.9.0...0.10.0
[0.9.0]: https://github.com/greglook/cljstyle/compare/0.8.3...0.9.0
[0.8.3]: https://github.com/greglook/cljstyle/compare/0.8.2...0.8.3
[0.8.2]: https://github.com/greglook/cljstyle/compare/0.8.1...0.8.2
[0.8.1]: https://github.com/greglook/cljstyle/compare/0.8.0...0.8.1
[0.8.0]: https://github.com/greglook/cljstyle/compare/0.7.0...0.8.0
[0.7.0]: https://github.com/greglook/cljstyle/compare/0.5.6...0.7.0
[0.5.6]: https://github.com/greglook/cljform/releases/tag/0.5.6
