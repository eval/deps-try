# deps-try

Quickly try out libraries using the [rebel-readline](https://github.com/bhauman/rebel-readline#rebel-readline).

It's basically [lein-try](https://github.com/avescodes/lein-try) but for [tools-deps](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools).

## Quick try

*NOTE1*: use `clojure` not `clj` (needed for rebel-readline)
*NOTE2*: once the repl is up run `:repl/try` to load the dependencies.

```bash
$ clojure -Sdeps '{:deps {deps-try {:git/url "https://gitlab.com/eval/deps-try" :sha "66d32db70bdb913c2c3ac35dafa27f2f978324e0"}}}' -m deps-try.main clj-time
[Rebel readline] Type :repl/help for online help info
user=> :repl/try     ;; <=== REQUIRED
Adding lib clj-time RELEASE
Done! Deps can now be required, e.g: (require '[some-lib.core :as sl])
user=> (require '[clj-time :as t])
...
```

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


## Examples

Show usage:

```bash
;; when no args provided or with -h/--help
$ clojure -A:deps-try
Usage:
  deps-try dep [dep-version] [other-dep ...]

Then in the REPL run `:repl/try` and require the libraries.
```

Specific versions

```
$ clojure -A:deps-try clj-time "0.14.2"
...
user=> :repl/try
```

Multiple dependencies:

```
$ clojure -A:deps-try clj-time com.datomic/datomic-free
...
user=> :repl/try
```

## TODO

- ensure `:repl/try` no longer needed
- use https://github.com/hagmonk/find-deps
 
## LICENSE

Copyright (c) 2018 Gert Goet, ThinkCreate
Distributed under the MIT license. See LICENSE.

Parts from [lein-try](https://github.com/avescodes/lein-try), Copyright (c) 2013 Ryan Neufeld, distributed under the Eclipse Public License 1.0.