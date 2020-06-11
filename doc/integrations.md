## Integrations

`cljstyle` can be integrated into a number of different tools.

### ZSH

If you use `zsh` as your shell, you can add completion for `cljstyle` by
placing the [completion file](completion.zsh) somewhere on your `$fpath` and
naming it `_cljstyle`. This will complete the commands and tool options.


### Vim

For a simple vim integration you can use the following function to reformat the
current buffer:

```vim
" Add to file for vim or neovim:
" ~/.vim/after/ftplugin/clojure.vim
" ~/.config/nvim/after/ftplugin/clojure.vim

" NOTE: typically you'd set these to use a formatter, but in this case it fails
" since cljstyle usually can't run on partial forms.
"setlocal equalprg=cljstyle\ pipe
"setlocal formatprg=cljstyle\ pipe

" This can also go in autoload/cljstyle.vim
function cljstyle#fix()
    let cwd = getcwd()
    let winsave = winsaveview()
    execute "cd" . expand('%:p:h')

    :%!cljstyle pipe

    execute "cd" . cwd
    call winrestview(winsave)
endfunction

" Example shortcut to fix the current file
nnoremap <leader>cs :call cljstyle#fix()<cr>
```


### Emacs

The [cljstyle-mode](https://github.com/jstokes/cljstyle-mode) project offers a
`cljstyle` integration for Emacs users.


### tools.deps

If you would like to use `cljstyle` without installing the binary, you can run
it directly from the `clj` CLI:

```shell
clj -Sdeps '{:deps {mvxcvi/cljstyle {:git/url "https://github.com/greglook/cljstyle.git", :tag "0.12.1"}}}' \
    -m cljstyle.main \
    check
```


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
          CLJSTYLE_VERSION: 0.12.1
        command: |
          wget https://github.com/greglook/cljstyle/releases/download/${CLJSTYLE_VERSION}/cljstyle_${CLJSTYLE_VERSION}_linux.tar.gz
          tar -xzf cljstyle_${CLJSTYLE_VERSION}_linux.tar.gz
    - run:
        name: Check style
        command: "./cljstyle check --report"
```

This assumes you have defined a common executor configuration named `clojure`.
