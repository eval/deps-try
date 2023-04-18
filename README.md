# deps-try

Quickly try out Clojure and libraries on [rebel-readline](https://github.com/bhauman/rebel-readline#rebel-readline):

<img width="535" alt="Screenshot 2023-02-09 at 13 37 09" src="https://user-images.githubusercontent.com/290596/217814688-c72a6fa1-3378-47bf-ba3f-5e87eec22c8b.png">

> This README is aimed at people starting their Clojure journey as well as Clojure experts. If anything is not clear, or you learned something that might help other starters, please open an issue or start a new discussion 🌸

[![discuss at Clojurians-Zulip](https://img.shields.io/badge/clojurians%20zulip-clojure-brightgreen.svg)](https://clojurians.zulipchat.com/#narrow/stream/151168-clojure)

## Rationale

This tools targets both Clojure newcomers as well as Clojure experts.

Trying out Clojure is easier when you have code completion, syntax highlighting and function documentation and examples nearby. `deps-try` provides a REPL with exactly these IDE functionalities (and some more).   
This means there's no need to install or configure any Clojure plugins/extensions for your editor. Also you don't need to setup a project this way, so instead of diving in the nitty gritty details of a `deps.edn` configuration file, you can start writing Clojure.  

Quickly trying out one or more libraries is also easy with `deps-try`:
```
$ deps-try some-maven/library com.github.user/a-git-project
```
Again, no need to setup or adjust a project, or type out the full configuration at the command line.


## Features

- always use the latest release of Clojure.
- conveniently use dependencies from maven/clojars and various git-hostings.
- add dependencies _without_ restarting the REPL.
- dependencies are resolved in isolation (as much as possible)
  - ...ignoring global, project or project deps.edn.
- rebel-readline provides:
  - syntax highlighting and indentation
  - code completion
  - see the docstring and source of a function
- deps-try extends rebel-readline with:
  - see examples of a function from clojuredocs.org
  - pprint results with syntax highlighting
  - interrupt operations without quiting the REPL
  - easier copy/paste of multiline code
  - improved support for eval-at-point (e.g. set and list literals, var quote, deref)
- toggle Clojure settings
  - `clojure.core/*print-meta*`
  - `clojure.core/*print-namespace-maps*` (default off)

## Installation

### Homebrew (Linux and macOS)

#### Prerequisites

Ensure you have a [Clojure compatible Java version](https://clojure.org/guides/install_clojure#java).

Verify that the following commands work:

``` bash
$ java -version
# example output
openjdk version "17.0.2" 2022-01-18
OpenJDK Runtime Environment Temurin-17.0.2+8 (build 17.0.2+8)
OpenJDK 64-Bit Server VM Temurin-17.0.2+8 (build 17.0.2+8, mixed mode)
```

#### Install

``` bash
$ brew install eval/brew/deps-try
# For future upgrades do:
$ brew update && brew upgrade deps-try
```

There's also the unstable releases (the latest master):
``` bash
$ brew install --head eval/brew/deps-try
# For future upgrades do:
$ brew update && brew reinstall deps-try
```

### bbin (Windows, Linux and macOS)

[bbin](https://github.com/babashka/bbin) allows for easy installation of Babashka scripts (such as deps-try).

It's currently the only way to install deps-try on Windows.

#### Prerequisites

Ensure you have a [Clojure compatible Java version](https://clojure.org/guides/install_clojure#java).

Also: [install bbin](https://github.com/babashka/bbin#installation) (make sure to adjust $PATH).

Verify that the following commands work:

``` bash
$ java -version
# example output
openjdk version "17.0.2" 2022-01-18
OpenJDK Runtime Environment Temurin-17.0.2+8 (build 17.0.2+8)
OpenJDK 64-Bit Server VM Temurin-17.0.2+8 (build 17.0.2+8, mixed mode)
$ bbin --version
# example output
bbin 0.1.12
```

#### Installation


``` bash
$ bbin install https://github.com/eval/deps-try/releases/download/stable/deps-try.jar

# Check version
$ deps-try -v

# Re-run the install command to upgrade
```

### manual (Windows, Linux and macOS)

#### Prerequisites

* Install [Clojure](https://clojure.org/guides/install_clojure)
* Install [babashka](https://github.com/babashka/babashka#installation)

Verify that the following commands work:

``` bash
$ clj
# REPL starts successfully, ergo Clojure and Java are correctly configured.
$ bb --version
babashka v1.3.176
```

#### Installation

* Download [the latest stable bb-jar](https://github.com/eval/deps-try/releases/tag/stable).
* Put an executable wrapper-script on $PATH. For example (for Linux and macOS):
```bash
#!/usr/bin/env sh

exec bb /absolute/path/to/deps-try-bb.jar "$@"
```

## Usage

``` bash
# A REPL using the latest Clojure version
$ deps-try

# A REPL with specific dependencies (latest version implied)
$ deps-try metosin/malli criterium/criterium

# ...specific version
$ deps-try metosin/malli 0.9.2

# Dependency from GitHub/GitLab/SourceHut (gets you the latest SHA from the default branch)
$ deps-try https://github.com/metosin/malli

# ...a specific branch/SHA
$ deps-try https://github.com/metosin/malli some-branch-sha-or-tag

# ...using the 'infer' notation, e.g.
# com.github.<user>/<project>, com.gitlab.<user>/<project>, ht.sr.<user>/<project>
$ deps-try com.github.metosin/malli
```

During a REPL-session:

``` clojure
# see help for all options
user=> :repl/help

# add dependencies
user=> :deps/try dev.weavejester/medley
```

## Bindings
| Binding | Comment |  |
| --- | :-- | --: |
| <kbd>TAB</kbd> / <kbd>Ctrl</kbd> + <kbd>I</kbd> | Indent or complete. | ![deps-try-tab](https://user-images.githubusercontent.com/290596/229816619-7f084076-df8b-4508-82d0-a7cde0a0f974.gif) |
| <kbd>Ctrl</kbd> + <kbd>X</kbd> <kbd>Ctrl</kbd> + <kbd>A</kbd> | Apropos. Search all public vars in loaded namespaces matching word for cursor. | ![deps-try-apropos](https://user-images.githubusercontent.com/290596/229820298-55c3a1e6-0fa1-4a84-b0d1-a04d8ae7ed85.gif)|
|<kbd>Ctrl</kbd> + <kbd>X</kbd> <kbd>Ctrl</kbd> + <kbd>D</kbd>| Show doc of function (or namespace) using word before cursor. |<img width="624" alt="Screenshot 2023-04-04 at 15 38 12" src="https://user-images.githubusercontent.com/290596/229811188-cd9775e0-6f06-4300-a457-90b8d891e808.png">|
| <kbd>Ctrl</kbd> + <kbd>X</kbd> <kbd>Ctrl</kbd> + <kbd>E</kbd> | Eval expression before cursor. |  ![deps-try-eval](https://user-images.githubusercontent.com/290596/229825665-b1a40a81-6185-419c-bac2-4ce029890765.gif)|
| <kbd>Ctrl</kbd> + <kbd>X</kbd> <kbd>Ctrl</kbd> + <kbd>M</kbd> | Force accept line (when cursor is a position where <kbd>Return</kbd> would insert a newline). | ![deps-try-force-accept](https://user-images.githubusercontent.com/290596/229837792-bf1b19e6-33e2-4c3c-8cf9-e8adf3d887fc.gif) |
| <kbd>Ctrl</kbd> + <kbd>X</kbd> <kbd>Ctrl</kbd> + <kbd>S</kbd> | Show source of function using word before cursor. | <img width="623" alt="Screenshot 2023-04-04 at 17 26 47" src="https://user-images.githubusercontent.com/290596/229841609-293435c2-0d4e-4720-84c0-507448568a45.png"> |
|<kbd>Ctrl</kbd> + <kbd>X</kbd> <kbd>Ctrl</kbd> + <kbd>X</kbd>| Searches [clojuredocs](https://clojuredocs.org/core-library) for examples using word before cursor. |<img width="623" alt="Screenshot 2023-04-04 at 15 32 26" src="https://user-images.githubusercontent.com/290596/229809276-26bb6fa2-e780-40f6-94d3-80a0662af1ec.png">|
| <kbd>Ctrl</kbd> + <kbd>R</kbd> <kbd>Search term</kbd> <kbd>Ctrl</kbd> + <kbd>R</kbd> (backward) / <kbd>Ctrl</kbd> + <kbd>S</kbd> (forward) | Searches history for commands containing <kbd>Search term</kbd> | ![deps-try-search-history](https://user-images.githubusercontent.com/290596/229847045-d0ec6d88-4ecd-4114-bf17-e1f09b4a64e6.gif)|
| <kbd>Esc</kbd> + <kbd>Return</kbd> | Insert newline (where <kbd>Return</kbd> would otherwise submit line). | ![deps-try-insert-newline](https://user-images.githubusercontent.com/290596/229849928-c9532a81-4eda-4334-bbde-ca6acbf7a4ab.gif)|
| <kbd>Code</kbd> + <kbd>↑</kbd> | Searches history for lines starting with <kbd>Code</kbd> (e.g. find all requires, defs etc). | ![deps-try-arrow-up](https://user-images.githubusercontent.com/290596/229852412-12539ee4-0d17-4de9-937d-19060306908d.gif) |
| <kbd>Alt</kbd> + <kbd>p</kbd> / <kbd>Alt</kbd> + <kbd>n</kbd> | Step back-/forward through history _without_ stepping through every line of a history item (as <kbd>↑</kbd>/<kbd>↓</kbd> do).| |

## FAQ

<a name="use_rebel_readline"></a>
### How to use the vendored rebel-readline in isolation?

I got you:  

```bash
$ clojure -Sdeps '{:deps {com.github.eval/deps-try {:deps/root "vendor/rebel-readline/rebel-readline" :git/sha "3781e67c3afae3b51f414db1b12abe5ff33d480b"}}}' -m rebel-readline.main
```


## Credits

Big thanks to [Bruce Hauman](https://github.com/bhauman) and contributors for creating [rebel-readline](https://github.com/bhauman/rebel-readline) 🌸.  
While the [GitHub contributors page](https://github.com/eval/deps-try/graphs/contributors) now only shows a meager 1 commit from [Bruce Hauman](https://github.com/bhauman), this couldn't be farther from the truth obviously.

## LICENSE

Copyright (c) 2023 Gert Goet, ThinkCreate
Distributed under the MIT license. See LICENSE.

Code in vendor/rebel-readline originates from [rebel-readline](https://github.com/bhauman/rebel-readline) which is covered by the Eclipse Public License either version 1.0 or (at your option) any later version.
