# Changelog

## v0.5.0 (2023-06-01)

- Support local projects as dependency.  
  e.g. `deps-try . ~/some/project ../some/other/project`
- Better parsing of deps.  
  - check existance of deps and versions beforehand (online or offline).  
    This fixes ambiguity between `com.github.user/project` being a maven or github dependency. Both are now considered.
  - print helpful error message otherwise.
- Use Clojure v.1.12.0-alpha3.

## v0.4.0 (2023-04-15)

- Use Clojure v1.12.0-alpha2.
- Drop tools-deps dependency and use clojure.repl.deps/add-libs.  
  This shrunk the uberjar from 13.5MB to 2.2MB.  
  **NOTE** adding libraries in the REPL-session (i.e. via the :deps/try command) now requires Clojure CLI version >= 1.11.1.1273.
- Show warning for older versions of Clojure CLI.
- Require cljfmt.core and compliment.core in background when starting REPL instead of at first use.

## v0.3.9 (2023-04-08)

- Updated artifacts for homebrew.

## v0.3.8 (2023-04-08)

- Releases via Github Actions.
- Add installation via homebrew.
- Add version-flag.

## v0.3.6 (2023-04-04)

- Prevent gap between footer and bottom of window with display-less.
- Upgrade babashka/fs, org.babashka/http-client.
- Introduce examples-at-point (C-x C-x for word at point).
- Don't prepend multi-lines with '#_ => '. This makes code better copyable.
- Improve eval-at-point for literal sets/lists, var-quote-ed and deref-ed expressions.
- Support help flag.

## v0.3.5 (2023-02-10)

- [#2](https://github.com/eval/deps-try/pull/2): Fix basis-file issue ([@borkdude](https://github.com/borkdude))

## v0.2.0 (2023-02-02)

- Vendor rebel-readline
- Upgrade dependencies
- Make bbin-installable


## v0.1.0 (2018-11-30)

- Initial release
