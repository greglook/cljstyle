#compdef cljstyle

# ZSH completion for cljstyle.
# Drop this somewhere on your `$fpath` named `_cljstyle`.

_cljstyle() {
    _arguments \
        "--timeout=[seconds]:File processing timeout" \
        "--timeout-trace[dump thread traces on timeout]" \
        "--report[print stats report]" \
        "--report-timing[print detailed rule timing]" \
        "--stats=[statistics file]:Write formatting stats to this file:_files" \
        "--no-color[disable color output]" \
        "--verbose[verbose logging output]" \
        "--help[show usage information]" \
        "--exclude=[exclude globs]:File globs to exclude:_files" \
        "1:command:((find\\:'Find files which would be processed'
                     check\\:'Check source files and print a diff for errors'
                     fix\\:'Edit source files to fix formatting errors'
                     pipe\\:'Read stdin and write reformatted to stdout'
                     config\\:'Show config used for a given path'
                     version\\:'Print program version information'))" \
        "*::path:_files"
}
