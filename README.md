cljstyle
========

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/greglook/cljstyle/tree/main.svg?style=shield)](https://dl.circleci.com/status-badge/redirect/gh/greglook/cljstyle/tree/main)
[![codecov](https://codecov.io/gh/greglook/cljstyle/branch/main/graph/badge.svg)](https://codecov.io/gh/greglook/cljstyle)
[![Clojars](https://img.shields.io/clojars/v/mvxcvi/cljstyle.svg)](https://clojars.org/mvxcvi/cljstyle)

`cljstyle` is a tool for formatting Clojure code. It can take something messy
like this:

```clojure
(  ns
 foo.bar.baz  "some doc"
    (:require (foo.bar [abc :as abc]
        def))
    (:use foo.bar.qux)
    (:import foo.bar.qux.Foo
      ;; Need this for the thing
      foo.bar.qux.Bar)
    )

(defn hello "says hi" (
      [] (hello "world")
  ) ([name]
  ( println "Hello," name  )
  ))
```

...and restyle it into nicely-formatted code like this:

```clojure
(ns foo.bar.baz
  "some doc"
  (:require
    [foo.bar.abc :as abc]
    [foo.bar.def]
    [foo.bar.qux :refer :all])
  (:import
    (foo.bar.qux
      ;; Need this for the thing
      Bar
      Foo)))


(defn hello
  "says hi"
  ([] (hello "world"))
  ([name]
   (println "Hello," name)))
```

Note that this is a rewrite of the original
[weavejester/cljfmt](https://github.com/weavejester/cljfmt/issues) tool to
provide more capabilities and configurability as well as a native-compiled
binary.


## Installation

### Manual install
Binary releases are available on the [GitHub project](https://github.com/greglook/cljstyle/releases).
The native binaries are self-contained, so to install them simply place them on
your path.

### Installation script (macOS and Linux)

This installation script works for linux and MacOS and can be used for quickly
installing or upgrading to the newest cljstyle without a package manager. It
will install to `/usr/local/bin` by default.

To download and execute the script:

```
curl -sLO https://raw.githubusercontent.com/greglook/cljstyle/main/util/install-cljstyle
chmod +x install-cljstyle
./install-cljstyle
```

The script accepts several options to control the installation directory,
download directory, version, and architecture. Run with `--help` to see all
options. To upgrade, just run the script again.

### macOS via Homebrew

`cljstyle` can be installed on macOS via a [Homebrew Cask](https://github.com/Homebrew/homebrew-cask):

```bash
brew install --cask cljstyle
```

The `cljstyle` binary is not code-signed, so you may also need to explicitly unquarantine it by running:

```bash
xattr -d com.apple.quarantine $(which cljstyle)
```

### Clojars
Releases are also published to Clojars, so it can be used with any Maven-compatible
tool. See the [integration docs](doc/integrations.md) for instructions on invoking
from Leiningen and tools.deps.


## Usage

The `cljstyle` tool supports several different commands for checking source files.

### Check and Fix

To check the formatting of your source files, use:

```
cljstyle check
```

If the formatting of any source file is incorrect, a diff will be supplied
showing the problem, and what cljstyle thinks it should be.

If you want to check only a specific file, or several specific files,
you can do that, too:

```
cljstyle check src/foo/core.clj
```

Once you've identified formatting issues, you can choose to ignore them, fix
them manually, or let cljstyle fix them with:

```
cljstyle fix
```

As with the `check` task, you can choose to fix a specific file:

```
cljstyle fix src/foo/core.clj
```

The `pipe` command offers a generic way to correct style by reading Clojure
code from stdin and writing the reformatted code to stdout:

```
cljstyle pipe < in.clj > out.clj
```

This command resolves configuration from the directory it is executed in, since
there is no explicit file path to use.

### Debugging

For inspecting what cljstyle is doing, one tool is to specify the `--verbose`
flag, which will cause additional debugging output to be printed. There are also
a few extra commands which can help understand what's happening.

The `find` command will print what files would be checked by cljstyle. It will
print each file path to standard output on a new line:

```
cljstyle find [path...]
```

The `config` command will show what configuration settings cljstyle would use to
process the specified files or files in the current directory:

```
cljstyle config [path]
```

Finally, `version` will show what version of the tool you're using:

```
cljstyle version
```

### Integrations

`cljstyle` can be integrated into many different tools, including shells,
editors, and tests. See the [integration docs](doc/integrations.md) for more
details.


## Configuration

The `cljstyle` tool comes with a sensible set of default configuration built-in
and may additionally be configured by using a hierarchy of `.cljstyle` files in
the source tree. The [configuration settings](doc/configuration.md) include
toggles for format rules, width constraints, and the
[indentation rules](doc/indentation.md).


## Ignoring Forms

By default, cljstyle will ignore forms which are wrapped in a `(comment ...)` form
or preceeded by the discard macro `#_`. You can also optionally disable
formatting rules from matching a form by tagging it with `^:cljstyle/ignore`
metadata - this is often useful for macros.


## License

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
