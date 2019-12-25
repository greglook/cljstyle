## Editor Integration

### Vim

For a simple vim integration copy the following into your `vimrc`:

```vimscript
function! Cljstyle()
    let cwd = getcwd()
    let winsave = winsaveview()
    execute "cd" . expand('%:p:h')

    :%!cljstyle pipe

    execute "cd" . cwd
    call winrestview(winsave)

endfunction

command! Cljstyle :undojoin | call Cljstyle()
```

Calling `Cljstyle` will run `cljstyle pipe` on the current buffer.

You can optionally add this line to auto cljstyle on save:

```vimscript
autocmd BufWritePre *.cl* Cljstyle
```
