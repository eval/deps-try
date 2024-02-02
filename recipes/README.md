# REPL recipes

This folder contains built-in recipes. Recipes are essentially Clojure source files that (if you were to load it via the CLI: `deps-try --recipe builtin/recipe`) gets chopped up in individual expressions that get front-loaded in the REPL's history.
This allows someone to step through code by just continuously selecting the most recent item from the REPL history.  
A recipe can denote libraries as dependencies. These will be put on the classpath when loading the recipe. A recipe is great in that way to provide code tutorials.

## Usage

### available recipes

```bash
# --refresh ensures latest manifest-file is loaded
$ deps-try-dev recipes [--refresh]
name                    title
──────────────────────  ─────────────────────────────────────────────────────────────────────────────────────
deps-try/recipes        Introducing recipes
malli/malli-select      Introduction to malli-select, a library for spec2-inspired selection of Malli-schemas
next-jdbc/intro-sqlite  A next-jdbc introduction using SQLite
portal/intro            Introduction to portal, a Clojure tool to navigate data
```

### starting recipe

Builtin recipe:
```bash
$ deps-try --recipe deps-try/recipes
```

Non built-in recipe
```bash
$ deps-try --recipe https://gist.githubusercontent.com/eval/ee80ebddaa120a7732396cea8cfc96da/raw
```

## LICENSE

<p xmlns:cc="http://creativecommons.org/ns#" xmlns:dct="http://purl.org/dc/terms/"><span property="dct:title">All files in this folder</span> are licensed under <a href="http://creativecommons.org/licenses/by/4.0/?ref=chooser-v1" target="_blank" rel="license noopener noreferrer" style="display:inline-block;">CC BY 4.0<img style="height:22px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/cc.svg?ref=chooser-v1"><img style="height:22px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/by.svg?ref=chooser-v1"></a></p>
