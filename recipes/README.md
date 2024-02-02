# REPL Recipes

This folder contains built-in recipes. Recipes are essentially Clojure source files that (if you were to load it via the CLI: `deps-try --recipe builtin/recipe`) gets chopped up in individual expressions that get front-loaded in the REPL's history.
This allows someone to step through code by just continuously selecting the most recent item from the REPL history.  
A recipe can denote libraries as dependencies which will then be put on the classpath when loading the recipe.
Recipes can in this way be used as tutorials, bug reports, or as snippets to quickly jump into a domain with the right dependencies, ns-aliases and helper-functions.

<div align="center">
<p>
<!-- <sup>
<a href="https://polar.sh/eval/subscription">My open source work is supported by the community</a>
</sup> -->
</p>
<sup>REPL Recipes are made possible by:</sup>
<br>
<br>
<a href="https://www.clojuriststogether.org/">
	<img src="https://github.com/eval/deps-try/assets/290596/b74c3688-e778-47ea-abd4-0ab920b1d5ef" width="160"/>

  <!-- <b>Some description</b> -->
</a>
<br>
<br>
  <a href="https://polar.sh/eval/subscriptions">Your logo here?</a>
</div>

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

If you have suggestions for (new) recipes: PRs are welcome!

### loading recipe

Builtin recipe:
```bash
$ deps-try --recipe deps-try/recipes
```

Non built-in recipe
```bash
# url
$ deps-try --recipe https://gist.github.com/eval/ee80ebddaa120a7732396cea8cfc96da/raw

# local recipe
$ deps-try --recipe ./path/to/recipe.clj
```

## LICENSE

<p xmlns:cc="http://creativecommons.org/ns#" xmlns:dct="http://purl.org/dc/terms/"><span property="dct:title">All files in this folder</span> are licensed under <a href="http://creativecommons.org/licenses/by/4.0/?ref=chooser-v1" target="_blank" rel="license noopener noreferrer" style="display:inline-block;">CC BY 4.0<img style="height:22px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/cc.svg?ref=chooser-v1"><img style="height:22px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/by.svg?ref=chooser-v1"></a></p>
