## Editor Integration

### Vim

For a simple vim integration copy the following into your `.vimrc` or neovim
`.config/nvim/init.vim`:

```vimscript
function! cljstyle()
    let cwd = getcwd()
    let winsave = winsaveview()
    execute "cd" . expand('%:p:h')

    :%!cljstyle pipe

    execute "cd" . cwd
    call winrestview(winsave)
endfunction
```

Calling `cljstyle()` will run `cljstyle pipe` on the current buffer.

You can optionally add this line to auto cljstyle on save:

```vimscript
autocmd BufWritePre *.clj* call cljstyle()
```
