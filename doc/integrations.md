## Integrations

`cljstyle` can be integrated into a number of different tools.

### ZSH

If you use `zsh` as your shell, you can add completion for `cljstyle` by
placing the [completion file](completion.zsh) somewhere on your `$fpath` and
naming it `_cljstyle`. This will complete the commands and tool options.


### Vim

For a simple vim integration set either `'equalprg'` (or `'formatprg'`) for 
the clojure filetype:

```vim
" ~/.vim/after/ftplugin/clojure.vim

setlocal equalprg=cljstyle\ pipe
" or setlocal formatprg=cljstyle\ pipe
```

Use the `=` operator (or the `gq` operator) to filter the selected lines
through `cljstyle pipe`.


### tools.deps

If you would like to use `cljstyle` without installing the binary, you can run
it directly from the `clj` CLI:

```shell
clj -Sdeps '{:deps {mvxcvi/cljstyle {:git/url "https://github.com/greglook/cljstyle.git", :tag "VERSION"}}}' \
    -m cljstyle.main \
    check
```

Note that you will have to replace `VERSION` with an appropriate tag.


### CircleCI

To keep your code well styled, you can run `cljstyle` as part of a CircleCI
workflow. The following job snippet will fetch the tool and check your sources:

```yaml
style:
  executor: clojure
  steps:
    - checkout
    - run:
        name: Install cljstyle
        environment:
          CLJSTYLE_VERSION: 0.12.0
        command: |
          wget https://github.com/greglook/cljstyle/releases/download/${CLJSTYLE_VERSION}/cljstyle_${CLJSTYLE_VERSION}_linux.tar.gz
          tar -xzf cljstyle_${CLJSTYLE_VERSION}_linux.tar.gz
    - run:
        name: Check style
        command: "./cljstyle check --report"
```

This assumes you have defined a common executor configuration named `clojure`.
