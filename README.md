# deps-try

Quickly try out Clojure dependencies on [rebel-readline](https://github.com/bhauman/rebel-readline#rebel-readline).

[![discuss at Clojurians-Zulip](https://img.shields.io/badge/clojurians%20zulip-clojure-brightgreen.svg)](https://clojurians.zulipchat.com/#narrow/stream/151168-clojure)

## Features

- always get the latest (possibly alpha) release of Clojure
- add dependencies on start or during the REPL session
- add dependencies from maven, clojars or various git-hostings.
- ignores deps.edn (global, user or in current folder)
- fanciness of rebel-readline:
  - syntax highlighting and indentation
  - code completion
  - see doc and source of a function
- added fanciness for rebel-readline:
  - pprint results with syntax highlighting
  - kill operations without quiting REPL
- easily toggle clojure settings
  - clojure.core/*print-meta*
  - clojure.core/*print-namespace-maps* (default off)

## Installation

### Prerequisites

- [Clojure](https://clojure.org/guides/install_clojure)
- [bbin](https://github.com/babashka/bbin#installation)

Verify that the following commands work:

``` bash
$ clojure --version
# => e.g. 'Clojure CLI version 1.11.1.1208'
$ bbin --version
# => e.g. 'bbin 0.1.8'
```

### Install deps-try

``` bash
$ bbin install io.github.eval/deps-try
```

This will use the latest tag in the repository, i.e. rerun this command to upgrade.


## Usage

``` bash
# A REPL using the latest Clojure version
$ deps-try

# A REPL with specific dependencies (latest version implied)
$ deps-try metosin/malli criterium/criterium

# ...specific version
$ deps-try metosin/malli 0.9.2

# Dependency from github
$ deps-try com.github.metosin/malli
$ deps-try https://github.com/metosin/malli some-branch-sha-or-tag
```

During a REPL-session:

``` clojure
# see help
user=> :repl/help

# add dependencies
user=> :deps/try metosin/malli
```

## LICENSE

Copyright (c) 2023 Gert Goet, ThinkCreate
Distributed under the MIT license. See LICENSE.
