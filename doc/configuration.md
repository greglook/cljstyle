Configuration
=============

The `cljstyle` tool reads configuration from `.cljstyle` files which may be
placed in any directories to control cljstyle's behavior on files in those
subtrees. These files are regular Clojure files which should contain a map of
settings to use:

```clojure
;; cljstyle configuration
{:files {:ignore #{"checkouts" "target"}}
 :rules {:blank-lines {:max-consecutive 3}}}
```


## Configuration Layout

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
{:rules {:indentation {:indents ^:replace {#".*" [[:inner 0]]}}}}
```


## File Settings

You can configure the way `cljstyle` looks for source files with the following
settings under the `:files` key:

* `:extensions`

  Set of string extensions (omitting the `.`) of files to consider. A value of
  `"clj"` would match `foo.clj`, `bar.clj`, etc. Includes all Clojure,
  ClojureScript, and cross-compiled files by default.

* `:pattern`

  Pattern to match against filenames to determine which files to check.

* `:ignore`

  Set of strings or patterns of files to ignore. Strings are matched against
  file and directory names exactly; patterns are matched against the entire
  (relative) file path. Ignored files will not be checked and ignored
  directories will not be recursed into.


## Format Rules

`cljstyle` has many formatting rules, and these can be selectively enabled or
disabled. The rule configuration is a map under the `:rules` key, with one
entry per formatting rule. The key is a rule keyword, and values are maps of
options for that rule. All rules support an `:enabled?` option, which can be
used to completely enable or disable that rule from running.

### `:indentation`

This rule corrects the indentation of code forms by rewriting the number of
leading spaces on each line.

* `:list-indent`

  Control indent size of lists. The default is 2 spaces. If this setting is 1,
  lists are formatted as follows.

  ```clojure
  (foo
   bar
   baz)
  ```

* `:indents`

  A map of indentation patterns to vectors of rules to apply to the matching
  forms. See the [indentation doc](indentation.md) for details.

### `:whitespace`

This rule corrects whitespace between and around forms.

* `:remove-surrounding?`

  Whether to remove whitespace surrounding inner forms. This will convert
  `(  foo  )` to `(foo)`.

* `:remove-trailing?`

  Whether to remove trailing whitespace in lines. This will convert
  `(foo)   \n` to `(foo)\n`.

* `:insert-missing?`

  Whether to insert whitespace missing from between elements. This will convert
  `(foo(bar))` to `(foo (bar))`.

### `:blank-lines`

This rule corrects the number of blank lines between top-level forms.

* `:trim-consecutive?`

  Whether to collapse consecutive blank lines. Any runs of empty lines longer
  than the limit will be truncated. This will convert `(foo)\n\n\n\n(bar)` to
  `(foo)\n\n\n(bar)` with the default setting of 2.

* `:max-consecutive`

  The maximum number of consecutive blank lines to allow between top-level
  forms.

* `:insert-padding?`

  Whether to insert blank lines between certain top-level forms. Any multi-line
  form will be padded with at least the configured number of empty lines
  between it and other non-comment forms.

* `:padding-lines`

  The minimum number of blank lines to include between top-level multiline
  forms.

### `:eof-newline`

This rule requires all files to end with a newline character. One will be added
if it is not present. There is no configuration for this rule beyond the
`:enabled?` state.

### `:vars`

This rule corrects formatting of var definition forms like `def`. There is no
configuration for this rule beyond the `:enabled?` state.

### `:functions`

This rule corrects the formatting of function forms like `fn`, `defn`, and
`letfn`. There is no configuration for this rule beyond the `:enabled?` state.

### `:types`

This rule corrects the formatting of type definitions.

* `:types?`

  Whether to format type definition forms like `deftype` and `defrecord`.

* `:protocols?`

  Whether to format protocol definition forms like `defprotocol`.

* `:reifies?`

  Whether to format reified type forms like `reify`.

* `:proxies?`

  Whether to format proxied type forms like `proxy`.

### `:namespaces`

This rule corrects and standardizes the formatting of `ns` definitions.

* `:indent-size`

  Number of spaces to indent in ns forms.

* `:import-break-width`

  Threshold for breaking a single class import into a package import group. If
  the combined package and class name would be longer than this limit, it is
  represented as a singleton group. Classes under this threshold may be either
  fully qualified or grouped.
