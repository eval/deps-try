# deps-try

Quickly try out dependencies on [rebel-readline](https://github.com/bhauman/rebel-readline#rebel-readline).

It's basically [lein-try](https://github.com/avescodes/lein-try) but using [tools-deps](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools).

*canonical repository: https://gitlab.com/eval/deps-try*

[![discuss at Clojurians-Zulip](https://img.shields.io/badge/clojurians%20zulip-clojure-brightgreen.svg)](https://clojurians.zulipchat.com/#narrow/stream/151168-clojure)

## Requirements

Install the [Clojure command line tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools).

## Quick try


```bash
$ clojure -Sdeps '{:deps {deps-try {:git/url "https://gitlab.com/eval/deps-try" :sha "9ccf64be248d5d9aa641df9e94feaea913bc0687"}}}' -m deps-try.main clj-time
[Rebel readline] Type :repl/help for online help info
Loading dependency clj-time RELEASE
[deps-try] Dependencies loaded. They can now be required, e.g: (require '[some-lib.core :as sl])
user=> (require '[clj-time :as t])
...
```

*NOTE*: use `clojure` not `clj` (needed for rebel-readline)

Alternatively add as alias to `~/.clojure/deps.edn`:

```clojure
:aliases {
...
  :try {:extra-deps {deps-try {:git/url "https://gitlab.com/eval/deps-try"
                               :sha "9ccf64be248d5d9aa641df9e94feaea913bc0687"}}
        :main-opts ["-m" "deps-try.main"]}
...
}
```

## Usage

```bash
Usage:
  clojure -A:try [<dep-name> [<dep-version>]...]

Example:
$ clojure -A:try clj-time

# specific version
$ clojure -A:try clj-time "0.14.2"

# multiple deps
$ clojure -A:try clj-time org.clojure/core.logic

# Adding a dep from the repl
user=> :repl/try clj-time
```

## TODO

- use https://github.com/hagmonk/find-deps

## LICENSE

Copyright (c) 2020 Gert Goet, ThinkCreate
Distributed under the MIT license. See LICENSE.

Parts from [lein-try](https://github.com/avescodes/lein-try), Copyright (c) 2013 Ryan Neufeld, distributed under the Eclipse Public License 1.0.
