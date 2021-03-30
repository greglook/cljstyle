## Indentation Rules

There are a few types of indentation rules that can be applied to forms. These
are configured by a map of rule targets to indenters. Each rule is specified
with either a symbol or a regular expression pattern. Rules are matched against
forms in the following order:

1. Check the qualified form symbol against the rule, including namespace.
2. Check the _name_ of the form symbol against the rule symbol.
3. If the rule is a pattern, match it against the form symbol string.

This ordering allows you to provide specific rules for overlapping symbols from
different namespaces, e.g. differentiating `d/catch` from `catch`.

### Inner rules

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

### Block rules

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

Indents can be customized to use the nth argument as the basis for indenting.

For example,

```clojure
{foo [[:block 1 2]]}
```

Will indent from

```clojure
(foo bar baz
bang)
```

to 

```clojure
(foo bar baz
         bang)
```

### Stair rules

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
