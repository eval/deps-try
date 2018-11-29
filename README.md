# deps-try

Quickly try out libraries using the [rebel-readline](https://github.com/bhauman/rebel-readline#rebel-readline).

It's basically [lein-try](https://github.com/avescodes/lein-try) but for [tools-deps](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools).

*canonical repository: https://gitlab.com/eval/deps-try*

## Quick try


```bash
$ clojure -Sdeps '{:deps {deps-try {:git/url "https://gitlab.com/eval/deps-try" :sha "66d32db70bdb913c2c3ac35dafa27f2f978324e0"}}}' -m deps-try.main clj-time
[Rebel readline] Type :repl/help for online help info
Loading dependency clj-time RELEASE
[deps-try] Dependencies loaded. They can now be required, e.g: (require '[some-lib.core :as sl])
user=> (require '[clj-time :as t])
...
```

*NOTE*: use `clojure` not `clj` (needed for rebel-readline)

Alternatively add as alias to `~/.clojure/deps.edn`:

```
:aliases {
...
    :deps-try {:extra-deps
                {deps-try
                    {:git/url "https://gitlab.com/eval/deps-try",
                     :sha "66d32db70bdb913c2c3ac35dafa27f2f978324e0"}},
                    :main-opts ["-m" "deps-try.main"]}
...
}
```

Use via: `$ clojure -A:deps-try clj-time`


## Usage

Show usage:

```bash
$ clojure -A:deps-try
Usage:
  clojure -A:deps-try dep-name [dep-version] [dep2-name ...]

Example:
$ clojure -A:deps-try clj-time

# specific version
$ clojure -A:deps-try clj-time "0.14.2"

# multiple deps
$ clojure -A:deps-try clj-time org.clojure/core.logic
```

## TODO

- [X] ensure `:repl/try` no longer needed
- use https://github.com/hagmonk/find-deps

## LICENSE

Copyright (c) 2018 Gert Goet, ThinkCreate
Distributed under the MIT license. See LICENSE.

Parts from [lein-try](https://github.com/avescodes/lein-try), Copyright (c) 2013 Ryan Neufeld, distributed under the Eclipse Public License 1.0.
