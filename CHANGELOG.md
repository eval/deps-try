# Changelog

## Unreleased

- recipes: support regular gist-urls (without '/raw')

## v0.10.0 (2023-12-04)

- [!28](https://github.com/eval/deps-try/pull/28): introduce recipes  
  Try it out: `$ deps-try --recipe deps-try/recipes`.  
  See all built-in recipes: `$ deps-try recipes`.  
  This feature was sponsored by [Clojurists Together](https://www.clojuriststogether.org/) ✨.
- fancier help-message
- using Clojure v1.12.0-alpha5
- Fix: git-url seen as version of git-url before it ([#24](https://github.com/eval/deps-try/issues/24)).  

## v0.9.0 (2023-09-19)

- Revert allowing overrides. Fixes [#23](https://github.com/eval/deps-try/issues/23).  
- Add keybinding ^X^T to 'eval&tap at point'.  
  Invoked with empty prompt it taps the result of the last evaluation (*1 or exception).  
- Upgrade compliment, and unlocking:  
  - private vars are suggested using var-quote notation, e.g. `#'some-ns/|TAB`.  
  - private and deprecated suggested vars are shown in red.  

## v0.8.0 (2023-07-03)

- Suggestions UI:
  - don't limit amount (let jline handle this).  
  - vars show up in yellow.  
  - suggested namespaces that are not required show up in magenta.  
  - sort namespace suggestions based on 'depth'.  
- Fix clojuredocs-url of namespaces.  
- Sort apropos results by length.  
- Upgrade [compliment/compliment](https://github.com/alexander-yakushev/compliment).  
  Includes provided PRs:
  - suggest namespaces from cljc files.  
    e.g. TAB-ing at `(require '[malli.co|` now suggests `malli.core`.  
  - docs of ns-alias work just like the full ns.  
  - aliases can be completed.  
  - completions and documentation also work when symbols are preceded by literals.  
    e.g. `#'some-ns/some-db`
- source and examples also work when symbols are preceded by literals.  
- docs now work for special forms.  
- highlighter
  - highlight ns-aliases.  
  - don't highlight slash in `clojure.core/`.  
- allow to override project-deps.  
  e.g. `$ deps-try ~/projects/compliment-fork`
- Use clojure 1.12.0-alpha4.  

## v0.7.0 (2023-06-16)

- Add CWD to classpath.  
  Ensures requiring of and using clojure.java.io/resource with local files works as expected.
- Completion picks up on :deps/try-ed libraries.
- Increase amount of history items.  
  500 -> 10K
- [#18](https://github.com/eval/deps-try/issues/18): Show parsing error in red when doing `:deps/try`.
- [!20](https://github.com/eval/deps-try/pull/20): Fix bbin install instructions ([@mdiin](https://github.com/mdiin)).

## v0.6.0 (2023-06-07)

- Support trying branch of PR or MR's.  
  e.g. the branch belonging to PR #123: `deps-try com.github.user/project ^123`.
- Force killing all thread pools. Fixes [#16](https://github.com/eval/deps-try/issues/16).

## v0.5.0 (2023-06-01)

- Support local projects as dependency.  
  e.g. `deps-try . ~/some/project ../some/other/project`
- Better parsing of deps.  
  - check existance of deps and versions beforehand (online or offline).  
    This fixes ambiguity between `com.github.user/project` being a mvn, git or even local dependency. All are now considered.
  - print helpful error message otherwise.
- Use Clojure v.1.12.0-alpha3.
- Add FAQ and rationale to README.

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
