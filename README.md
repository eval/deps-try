# deps-try

Quickly try out Clojure dependencies on [rebel-readline](https://github.com/bhauman/rebel-readline#rebel-readline):

<img width="535" alt="Screenshot 2023-02-09 at 13 37 09" src="https://user-images.githubusercontent.com/290596/217814688-c72a6fa1-3378-47bf-ba3f-5e87eec22c8b.png">

> This README is aimed at people starting their Clojure journey as well as Clojure experts. If anything is not clear, or you learned something that might help other starters, please open an issue or start a new discussion 🌸

[![discuss at Clojurians-Zulip](https://img.shields.io/badge/clojurians%20zulip-clojure-brightgreen.svg)](https://clojurians.zulipchat.com/#narrow/stream/151168-clojure)

## Features

- always use the latest (possibly alpha) release of Clojure.
- conveniently use dependencies from maven/clojars and various git-hostings.
- add dependencies without restarting the REPL.
- run dependencies in isolation (as much as possible)
  - ...ignoring deps.edn (global, user or in current folder).
- fanciness of rebel-readline:
  - syntax highlighting and indentation
  - code completion
  - see doc and source of a function
- deps-try added fanciness for rebel-readline:
  - see examples of a function from clojuredocs.org
  - pprint results with syntax highlighting
  - interrupt operations without quiting the REPL
  - easier copy/paste of multiline code
  - improved support for eval-at-point (e.g. set and list literals, var quote, deref)
- toggle Clojure settings
  - `clojure.core/*print-meta*`
  - `clojure.core/*print-namespace-maps*` (default off)

## Installation

### Prerequisites

- [Clojure](https://clojure.org/guides/install_clojure)
- [babashka](https://github.com/babashka/babashka#installation)
- [bbin](https://github.com/babashka/bbin#installation)

<details><summary>What's all this...?</summary><p>

Yes, I'm aware this list might be a bit intimidating for newcomers. But bear with me!  
Let's go over the items and see why we need them, and why it's worth to install these tools (even if you stop `deps-try`-ing):

### Clojure

Well, there's no way around this: we'll be running the official Clojure REPL on the JVM.

It's not super convenient to start a regular Clojure REPL with dependencies loaded (nor does it allow for adding dependencies during a REPL session).  
`deps-try` tries to solve this, and it does this with the help of...

### Babashka

[Babashka](https://babashka.org/) ("the fast native Clojure scripting runtime") is _the way_ to write scripts in Clojure: it's fast (something that JVM Clojure is not particularly known for...), self-contained (no JVM needed) and comes with batteries included for typical scripts.

Basbashka's main role in `deps-try` is to turn the dependencies you pass it into the right format and start the JVM Clojure REPL.

### bbin

`bbin` allows for easy installation of Basbashka scripts (from existing places like Git, Maven, filesystem etc.).

This is how `deps-try` gets on your `$PATH`.

Hope that clears things up!

---
</p></details>

Verify that the following commands work:

``` bash
$ clojure --version
# => prints e.g. 'Clojure CLI version 1.11.1.1267'
$ bbin --version
# => prints e.g. 'bbin 0.1.12'
```

### Install deps-try

To install the most recent tag:

``` bash
$ bbin install io.github.eval/deps-try

# (For future reference) see what bbin scripts are installed
$ bbin ls
```

To upgrade: rerun the install command.

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

## LICENSE

Copyright (c) 2023 Gert Goet, ThinkCreate
Distributed under the MIT license. See LICENSE.

Code in vendor/rebel-readline has been adapted from [rebel-readline](https://github.com/bhauman/rebel-readline) which is covered by the Eclipse Public License either version 1.0 or (at your option) any later version.
