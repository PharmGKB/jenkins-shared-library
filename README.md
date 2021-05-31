# PharmGKB's Shared Library for Jenkins 

This Jenkins shared library has only been tested with multibranch pipelines.

Non-PharmGKB-specific functionality:

* Skips build if `[skip ci]` appears in all commit messages for the build
* Skips dependabot and PR builds
* Skips outdated builds (i.e. if there's a newer build in the queue with a non-`[skip ci]` commit)


PharmGKB-specific functionality:

* Checks modified files to see if API or website stages should proceed
