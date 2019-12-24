cljstyle
========

[![CircleCI](https://circleci.com/gh/greglook/cljstyle.svg?style=shield&circle-token=9576040ebe39e81406823481c98dc55a39d03c4d)](https://circleci.com/gh/greglook/cljstyle)
[![codecov](https://codecov.io/gh/greglook/cljstyle/branch/master/graph/badge.svg)](https://codecov.io/gh/greglook/cljstyle)

`cljstyle` is a tool for formatting Clojure code.

It can turn something like this:

```clojure
( let [x 3
    y 4]
  (+ (* x x
  )(* y y)
  ))
```

Into nicely formatted Clojure code like this:

```clojure
(let [x 3
      y 4]
  (+ (* x x) (* y y)))
```

Note that this is a rewrite of the original
[weavejester/cljfmt](https://github.com/weavejester/cljfmt/issues) tool to
provide more capabilities and configurability.


## Installation

Releases are published on the [GitHub project](https://github.com/greglook/cljstyle/releases).
The native binaries are self-contained, so to install them simply place them on
your path.

## Editor Integration

### Vim

For a simple vim integration copy the following into your `vimrc`

```vimscript

function! Cljstyle()
    let filename = expand('%:p')
    let cwd = getcwd()
    let pos = getpos('.')

    " Change to the directory of the file and run cljstyle
    let fileDir = strpart(filename, 0, stridx(filename, "/"))
    execute "cd " . fileDir

    :%!cljstyle pipe

    " Change back to the working directory
    execute "cd " . cwd
    call setpos('.', pos)

endfunction

command! Cljstyle :undojoin | call Cljstyle()

```

Calling `Cljstyle` will run `cljstyle pipe` on the current buffer.

You can optionally add `autocmd BufWritePre *.cl* Cljstyle` to auto cljstyle on save.


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


## Configuration

The `cljstyle` tool comes with a sensible set of default configuration built-in,
and reads additional configuration from `.cljstyle` files which may be placed
in any directories to control cljstyle's behavior on files in those subtrees.
These files are regular Clojure files which should contain a map of settings to
use:

```clojure
;; cljstyle configuration
{:max-blank-lines 3
 :file-ignore #{"checkouts" "target"}}
```

When `cljstyle` is run, it searches upwards in the filesystem to find parent
configuration, and as it searches in directories it will merge in local config
files. For example, in a tree like the following:

```
a
├── .cljstyle
└── b
    ├── c
    │   ├── .cljstyle
    │   └── foo.clj
    └── d
        ├── .cljstyle
        └── e
            └── bar.clj
```

Running `cljstyle` in directory `c` would use `a/.cljstyle` as the base
configuration and would combine in the `a/b/c/.cljstyle` configuration to check
`foo.clj`. Running it directly from directory `e` would look upwards and use the
combination of `a/.cljstyle` and `a/b/d/.cljstyle` for `bar.clj`.

Configuration maps are merged together in depth-order, so that more local
settings take precedence. As with Leiningen profiles, you can add metadata
hints. If you want to override all existing indents, instead of just supplying
new indents that are merged with the defaults, you can use the `:replace` hint:

```clojure
{:indents ^:replace {#".*" [[:inner 0]]}}
```

### File Settings

You can configure the way `cljstyle` looks for source files with the following
settings:

* `:file-pattern`

  Pattern to match against filenames to determine which files to check. Includes
  all Clojure, ClojureScript, and cross-compiled files by default.

* `:file-ignore`

  Set of strings or patterns of files to ignore. Strings are matched against
  file and directory names exactly; patterns are matched against the entire
  (relative) file path. Ignored files will not be checked and ignored
  directories will not be recursed into.

### Format Rules

`cljstyle` has many formatting rules, and these can be selectively enabled or
disabled:

* `:indentation?`

  Whether cljstyle should correct the indentation of your code.

* `:line-break-functions?`

  Whether cljstyle should enforce line breaks in function definitions.

* `:remove-surrounding-whitespace?`

  Whether cljstyle should remove whitespace surrounding inner forms. This will
  convert `(  foo  )` to `(foo)`.

* `:remove-trailing-whitespace?`

  Whether cljstyle should remove trailing whitespace in lines. This will convert
  `(foo)   \n` to `(foo)\n`.

* `:insert-missing-whitespace?`

  Whether cljstyle should insert whitespace missing from between elements. This
  will convert `(foo(bar))` to `(foo (bar))`.

* `:remove-consecutive-blank-lines?`

  Whether cljstyle should collapse consecutive blank lines. Any runs of empty
  lines longer than `:max-consecutive-blank-lines` will be truncated to the
  configured limit. The default limit is 2. This will convert
  `(foo)\n\n\n\n(bar)` to `(foo)\n\n\n(bar)`.

* `:insert-padding-lines?`

  Whether cljstyle should insert blank lines between certain top-level forms. Any
  multi-line form will be padded with at least `:padding-lines` empty lines
  between it and other non-comment forms. The defaults is 2 lines.

* `:rewrite-namespaces?`

  Whether cljstyle should rewrite namespace forms to standardize their layout.

* `:single-import-break-width`

  Control the threshold for breaking a single class import into a package import
  group. If the combined package and class name would be longer than this limit,
  it is represented as a singleton group. Classes under this threshold may be
  either fully qualified or grouped.

* `:require-eof-newline?`

  Require all files to end with a newline character. One will be added if it is
  not present.

### Indentation rules

There are a few types of indentation rules that can be applied to forms. These
are configured by a map of rule targets to indenters. Each rule is specified
with either a symbol or a regular expression pattern. Rules are matched against
forms in the following order:

1. Check the qualified form symbol against the rule, including namespace.
2. Check the _name_ of the form symbol against the rule symbol.
3. If the rule is a pattern, match it against the form symbol string.

This ordering allows you to provide specific rules for overlapping symbols from
different namespaces, e.g. differentiating `d/catch` from `catch`.

#### Inner rules

An `:inner` rule will apply a constant indentation to all elements at
a fixed depth. So an indent rule:

```clojure
{foo [[:inner 0]]}
```

Will indent all elements inside a `foo` form by two spaces:

```clojure
(foo bar
  baz
  bang)
  ```

While an indent rule like:

```clojure
{foo [[:inner 1]]}
```

Will indent all subforms one level in:

```clojure
(foo bar
  baz
  (bang
    quz
    qoz))
```

Sometimes it's useful to limit indentation to one argument of the
surrounding form. For example, `letfn` uses inner indentation only in
its binding vector:

```clojure
(letfn [(double [x]
          (* x 2))]   ;; special indentation here
  (let [y (double 2)
        z (double 3)]
    (println y
             z)))     ;; but not here
```

To achieve this, an additional index argument may be used:

```clojure
{letfn [[:inner 2 0]]}
```

This will limit the inner indent to depth 2 in argument 0.

#### Block rules

A `:block` rule is a little smarter. This will act like an inner
indent only if there's a line break before a certain number of
arguments, otherwise it acts like a normal list form.

For example, an indent rule:

```clojure
{foo [[:block 0]]}
```

Indents like this, if there are more than 0 arguments on the same line
as the symbol:

```clojure
(foo bar
     baz
     bang)
```

But indents at a constant two spaces otherwise:

```clojure
(foo
  bar
  baz
  bang)
```

#### Stair rules

A `:stair` rule is similar to `:block`, except that it tries to indent
test/expression clauses as pairs. This can be used as an alternative styling for
`case`, `cond`, `cond->`, etc.The expression forms will be given an extra level
of indentation if they are on their own line:

```clojure
{cond [[:stair 0]]}
```

```clojure
(cond
  a? :a
  b? :b)

(cond
  a?
    :a
  b?
    :b)
```


## Ignoring Forms

By default, cljstyle will ignore forms which are wrapped in a `(comment ...)` form
or preceeded by the discard macro `#_`. You can also optionally disable
formatting rules from matching a form by tagging it with `^:cljstyle/ignore`
metadata - this is often useful for macros.
