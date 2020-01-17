## Configuration

The `cljstyle` tool reads configuration from `.cljstyle` files which may be
placed in any directories to control cljstyle's behavior on files in those
subtrees. These files are regular Clojure files which should contain a map of
settings to use:

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

* `:list-indent-size`

  Control indent size of list. The default is 2 spaces. If this setting is 1,
  lists are formatted as follows.

  ```clojure
  (foo
   bar
   baz)
  ```

* `:indents`

  A map of indentation patterns to vectors of rules to apply to the matching
  forms. See the [indentation doc](indentation.md) for details.

* `:line-break-vars?`

  Whether cljstyle should enforce line breaks in var definitions.

* `:line-break-functions?`

  Whether cljstyle should enforce line breaks in function definitions.

* `:reformat-types?`

  Whether cljstyle should reformat type-related expressions like `defprotocol`,
  `deftype`, `defrecord`, `reify`, and `proxy` by inserting line breaks and
  padding lines.

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
