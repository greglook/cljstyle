Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

### Changed
- Switched from a hodgepodge of Leiningen/Make/Bash to a `tools.deps` and
  `tools.build` build chain.

### Added
- The tool looks for `.cljstyle.clj` and `.cljstyle.edn` as additional options
  for style configuration.
  [#95](https://github.com/greglook/cljstyle/issues/95)


## [0.15.1] - 2023-12-12

A handful of minor fixes in this release.

- Fix file `:ignore` configuration to work correctly with relative paths.
  [#91](https://github.com/greglook/cljstyle/pull/91)
- Specify platform in `Dockerfile` so it works out-of-the-box on other architectures.
  [#99](https://github.com/greglook/cljstyle/pull/99)
- Update dependency versions, use new coordinates.
  [#101](https://github.com/greglook/cljstyle/pull/101)


## [0.15.0] - 2021-03-30

This release adds a major missing element to `cljstyle`'s formatting rules,
covering comment indentation, padding, and prefixes. This version also sorts
string libspecs (such as from `npm`) in a more widely-adopted way. As a result,
this version is likely to result in a diff for files which passed the previous
code.

### Changed
- Javascript node libraries required with string names will always sort before
  symbol namespace requires, matching other tooling convention.
  [#45](//github.com/greglook/cljstyle/pull/45)

### Fixed
- Upgraded `rewrite-clj` v0 to v1. This solves a number of parsing-related
  issues with the old version. In particular, auto-resolved namespaced maps
  work as expected now.
  [#13](//github.com/greglook/cljstyle/issues/13)
  [#52](//github.com/greglook/cljstyle/issues/52)
- Infinity reader forms `##Inf` and `##-Inf` are supported.
  [#43](//github.com/greglook/cljstyle/issues/43)
- If a discard reader macro was the last subform, it would inadvertently cause
  early termination of the styling walk. The code correctly walks back up the
  zipper now.
  [#55](//github.com/greglook/cljstyle/issues/55)
- The ns reformatting rule preserves comments on `:import` and `:require` forms
  at the top level of the `ns` form.
  [#56](//github.com/greglook/cljstyle/issues/56)
- Comments are now subject to indentation rules.
  [#30](//github.com/greglook/cljstyle/issues/30)
- Comments no longer prevent padding lines from being enforced between
  top-level forms.

### Added
- Releases will now contain a `.zip` file in addition to the `.tar.gz` archive.
  Eventually, this will become the primary archive format for releases.
- If the first two characters of a file are `#!`, the formatter will ignore the
  first line of text and only style the rest of the file contents.
  [#70](//github.com/greglook/cljstyle/issues/70)
- The namespace formatting rule supports a new configuration flag
  `:break-libs?` which controls whether requires and imports will start on a
  new line.
  [#35](//github.com/greglook/cljstyle/issues/35)
- A new `:comments` formatting rule standardizes comment prefixes,
  differentiating between inline comments (`;`) and leading comments (`;;`).
  Prefixes are configurable.
  [#30](//github.com/greglook/cljstyle/issues/30)
- When file processing times out, cljstyle will print the current files each
  thread was working on and how long they've been processing. A warning will
  be printed if any file takes more than five seconds to process.
  [#63](//github.com/greglook/cljstyle/issues/63)


## [0.14.0] - 2020-11-07

This release significantly changes the way that `cljstyle` is configured.
Instead of a single flat map of options, configuration has been split up into
rule-specific and file-specific nested maps. This helps make the options more
understandable, less repetitive, and will support more nuanced configuration
options in the future.

Legacy configuration will still work, but the tool now emits a warning when it
loads files with the old style config. Use the new `migrate` command to
automatically update your config files. Eventually, the legacy format will be
deprecated.

In addition, formatting rules are now applied in many fewer passes over the
syntax tree. This results in a significant speedup for most workloads, measured
at about 2.3x the throughput of the previous version. Use the new
`--report-timing` option to show a detailed table of which rules the processing
time was spent in.

### Changed
- Configuration files have a new structure which is rule-oriented.
- Refactor blank line rules out of whitespace rules.
- Protocol keyword attributes must start on a new line.
- Formatting rules are now expressed via data structures which can be composed
  into a single pass over the syntax forms.
- The `--exclude` option has been renamed to `--ignore` and uses standard regex
  syntax instead of globs. This provides better consistency with the `:ignore`
  configuration for `:files`.

### Added
- All commands now warn when reading legacy configuration files.
- The `migrate` command will rewrite configuration files to use the new syntax.
- Configuration merging now supports `^:concat` on sequential values to have
  the value appended to instead of replacing the previous value.
- Type formatting can be controlled for individual kinds of types to separately
  enable protocols, types, reifies, and proxies.
- A new `--report-timing` option will enable detailed timing data for each
  formatting rule in the report output.
- The processing timeout can now be controlled with the `--timeout` option,
  providing an execution limit in seconds.
- The `--timeout-trace` option will print a dump of all threads' stack traces
  when processing times out.

### Fixed
- Safely read configuration files to avoid read-time eval attacks.
- Remove duplicated exception printing code now that reflections are fixed in
  `clojure.stacktrace`.
- Fixed a bug with inner-indent rule index limiting. This primarily affected
  `letfn` forms when function formatting was disabled.
  [#54](//github.com/greglook/cljstyle/issues/54)
- Fixed margin calculation for reader macro forms, in particular affecting
  record literals.
  [#24](//github.com/greglook/cljstyle/issues/24)


## [0.13.0] - 2020-06-13

### Added
- Added support for setting `--exclude` CLI options that allow you to specify
  directories/files to ignore at runtime.
  [#44](https://github.com/greglook/cljstyle/pull/44)
- Automatically download Graal SDK for builds.

### Fixed
- Namespace reformatting logic handles "attr-map" forms correctly now.
  [#50](//github.com/greglook/cljstyle/issues/50)
  [#51](//github.com/greglook/cljstyle/pull/51)
- Files failing because of a missing EOF newline will correctly show a diff in
  the `check` output.
  [#48](//github.com/greglook/cljstyle/issues/48)


## [0.12.1] - 2020-02-22

### Added
- Added `deps.edn` to support usage from tools.deps and the `clj` CLI.
  [#33](//github.com/greglook/cljstyle/pull/33)

### Changed
- Merged the `core` and `tool` projects back into one top-level project.


## [0.12.0] - 2020-02-07

### Added
- The tool accepts a `--report` option which will cause the execution stats to
  be printed at the end of a run.
- Processing errors are now rethrown with more information about the specific
  function that failed and the position in the file which was problematic.
  [#29](//github.com/greglook/cljstyle/issues/29)

### Fixed
- Many performance optimizations across the formatting rules.
- Bug causing a `NullPointerException` when formatting type methods with empty
  bodies.
- Lines following comments are indented correctly now.
  [#32](//github.com/greglook/cljstyle/pull/32)


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


[Unreleased]: https://github.com/greglook/cljstyle/compare/0.15.1...HEAD
[0.15.1]: https://github.com/greglook/cljstyle/compare/0.15.0...0.15.1
[0.15.0]: https://github.com/greglook/cljstyle/compare/0.14.0...0.15.0
[0.14.0]: https://github.com/greglook/cljstyle/compare/0.13.0...0.14.0
[0.13.0]: https://github.com/greglook/cljstyle/compare/0.12.1...0.13.0
[0.12.1]: https://github.com/greglook/cljstyle/compare/0.12.0...0.12.1
[0.12.0]: https://github.com/greglook/cljstyle/compare/0.11.1...0.12.0
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
