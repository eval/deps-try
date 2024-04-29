(ns eval.deps-try.deps-test
  (:require [babashka.process]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing are]]
            [clojure.tools.gitlibs :as gitlib]
            [eval.deps-try.deps :as sut]
            [eval.deps-try.fs :as fs]
            [nl.zeekat.data.sub :refer [sub?]]))

(set! clojure.core/*print-namespace-maps* false)

(deftest parse-args-test
  (testing "mvn"
    (are [args exp] (= {:recipe exp} (sut/parse-args {:args args}))
      '("foo/bar") [[:or
                     [[:dep/mvn "foo/bar" :latest]]
                     [[:dep/local "foo/bar" :latest]]]]

      ;; solve ambiguity...
      ;; ...force it to be local
      '("./foo/bar") [[:dep/local "./foo/bar" :latest]]
      '("~/foo/bar") [[:dep/local "~/foo/bar" :latest]]

      ;; ...pass mvn version
      '("foo/bar" "1.2.3") [[:dep/mvn "foo/bar" "1.2.3"]]
      '("foo/bar" "RELEASE") [[:dep/mvn "foo/bar" "RELEASE"]]

      ;; only mvn-version is considered for a mvn-dep
      '("foo/bar" "v1.2.3")
      [[:or [[:dep/mvn "foo/bar" :latest]] [[:dep/local "foo/bar" :latest]]]
       [:dep/local "v1.2.3" :latest]]))

  (testing "mvn and git"
    (are [args exp] (= {:recipe exp} (sut/parse-args {:args args}))
      '("com.github.user/project")
      [[:or
        [[:dep/mvn "com.github.user/project" :latest]]
        [[:dep/git "com.github.user/project" :latest]]
        [[:dep/local "com.github.user/project" :latest]]]]

      ;; solve ambiguity by adding thing that can only be git-version
      '("com.github.user/project" "main")
      [[:dep/git "com.github.user/project" "main"]]

      ;; when the thing added should not be seen as version
      '("com.github.user/project" "./main")
      [[:or
        [[:dep/mvn "com.github.user/project" :latest]]
        [[:dep/git "com.github.user/project" :latest]]
        [[:dep/local "com.github.user/project" :latest]]]
       [:dep/local "./main" :latest]]

      ;; exclude local by adding thing that can be version
      '("com.github.user/project" "1.2.3")
      [[:or
        [[:dep/mvn "com.github.user/project" "1.2.3"]]
        [[:dep/git "com.github.user/project" "1.2.3"]]]]

      '("com.github.user/project" "LATEST")
      [[:or
        [[:dep/mvn "com.github.user/project" "LATEST"]]
        [[:dep/git "com.github.user/project" "LATEST"]]]]))
  (testing "git"
    (are [args exp] (= {:recipe exp} (sut/parse-args {:args args}))
      '("https://github.com/user/project")
      [[:dep/git "https://github.com/user/project" :latest]]

      '("https://github.com/user/project.git")
      [[:dep/git "https://github.com/user/project.git" :latest]]

      '("https://github.com/user/project1" "https://github.com/user/project2")
      [[:dep/git "https://github.com/user/project1" :latest]
       [:dep/git "https://github.com/user/project2" :latest]]

      '("https://github.com/user/project" "main")
      [[:dep/git "https://github.com/user/project" "main"]]

      '("io.github.user/project" "^12")
      [[:dep/git "io.github.user/project" "^12"]]))

;; TODO bogus is a second arg that is not a version to the first, and that is no dep
  #_(testing "bogus?"
      (are [args exp] (= {:deps exp} (sut/parse-args {:args args}))

      ;; dep met missing version
        #_'("foo/bar" "")

      ;; TODO expected "v1.2.3" to be a mvn-version, but it's not
        #_#_'("foo/bar" "v1.2.3")
          [[:dep/mvn "foo/bar" :latest]
           [:dep/local "v1.2.3" :latest]]

        #_#_#_#_'("https://github.com/user/project.git")
              [[:dep/git "https://github.com/user/project.git" :latest]]

            '("https://github.com/user/project" "main")
          [[:dep/git "https://github.com/user/project" "main"]])))

(deftest resolve-version-test
  (testing "dep/mvn online"
    (testing "lib not-found"
      (with-redefs [sut/multi-url-test (fn [_urls _options]
                                         {:status :not-found})]
        (is (sub? {:error/id :resolve.mvn/library-not-found}
                  (:error (sut/resolve-version [:dep/mvn "metosin/malli" :latest]))))))
    (testing "lib found"
      (with-redefs [sut/multi-url-test (fn [_urls _options]
                                         {:status :found
                                          :body   "
<metadata modelVersion=\"1.1.0\">
  <groupId>metosin</groupId>
  <artifactId>malli</artifactId>
  <versioning>
    <versions>
      <version>1.2.3</version>
    </versions>
  </versioning>
</metadata>
"})]
        (is (= {:mvn/version {:mvn/version "RELEASE"}}
               (sut/resolve-version [:dep/mvn "metosin/malli" :latest])))
        (is (= {:mvn/version {:mvn/version "1.2.3"}}
               (sut/resolve-version [:dep/mvn "metosin/malli" "1.2.3"])))
        (is (sub? {:error/id :resolve.mvn/version-not-found}
                  (:error (sut/resolve-version [:dep/mvn "metosin/malli" "0.1.2"])))))))

  (testing "dep/mvn offline/unavailable"
    (with-redefs [sut/multi-url-test  (fn [_urls _options]
                                        {:status :offline})
                  sut/local-repo-path (constantly (fs/path (io/resource "m2/repository")))]
      (testing "with local copy"
        (is (= {:mvn/version {:mvn/version "RELEASE"}}
               (sut/resolve-version [:dep/mvn "com.github.seancorfield/next.jdbc" :latest])))
        (is (= {:mvn/version {:mvn/version "1.3.874"}}
               (sut/resolve-version [:dep/mvn "com.github.seancorfield/next.jdbc" "1.3.874"]))))
      (testing "without local copy"
        (is (sub? {:error/id :resolve.mvn/version-not-found-offline}
                  (:error (sut/resolve-version [:dep/mvn "com.github.seancorfield/next.jdbc" "1.2.3"]))))
        (is (sub? {:error/id :resolve.mvn/library-not-found-offline}
                  (:error (sut/resolve-version [:dep/mvn "org.clojure/clojure" :latest])))))))

  (symbol (requiring-resolve 'babashka.process/process))
  (with-redefs [])
  (testing "dep/git online"
    (let [gitlib-resolve-result (atom nil)
          mock-gitlib-resolve!  (fn [result]
                                  (reset! gitlib-resolve-result result))

          [bb-process-args bb-process-result] [(atom nil) (atom nil)]

          mock-bb-process!
          (fn [args result]
            (let [result-tpls    {:template/offline
                                  {:out "", :exit 128, :err "fatal: unable to access 'https://github.com/seancorfield/next-jdbc/': Could not resolve host: github.com\n"}
                                  :template/not-found
                                  {:out "" :exit 0}
                                  :template/found
                                  {:out  "24d55f3165ada3418699f86a4fe5c1d1ab93c141\trefs/heads/develop\n"
                                   :exit 0}}
                  [tpl-key data] ((juxt #(some-> % meta keys first) identity) result)
                  result         (merge (result-tpls tpl-key) data)]
              (reset! bb-process-args args)
              (reset! bb-process-result result)))]
      (with-redefs [clojure.tools.gitlibs/resolve
                    (fn [_url _sha]
                      (if (map? @gitlib-resolve-result)
                        (throw (ex-info (:cause @gitlib-resolve-result)
                                        {:err (:err @gitlib-resolve-result)}))
                        @gitlib-resolve-result))
                    babashka.process/process
                    (fn [& args]
                      (assert (sub? @bb-process-args args)
                              (str "Expected " (pr-str @bb-process-args) " to be a `sub?` of " (pr-str args)))
                      (delay @bb-process-result))]
        (testing ":latest"
          (mock-bb-process! '(["git" "ls-remote" "--symref" "https://:@github.com/seancorfield/next-jdbc" "HEAD"])
                            ^:template/found {:out "ref: refs/heads/develop\tHEAD\n24d55f3165ada3418699f86a4fe5c1d1ab93c141\tHEAD\n"})
          (is (sub? {:git/sha "24d55f3165ada3418699f86a4fe5c1d1ab93c141",
                     :git/ref "refs/heads/develop"}
                    (:git/version (sut/resolve-version
                                   [:dep/git "https://github.com/seancorfield/next-jdbc" :latest])))))
        (testing "a branch"
          (mock-bb-process! '(["git" "ls-remote" "--symref" "https://:@github.com/seancorfield/next-jdbc" "develop"])
                            ^:template/found {:out "24d55f3165ada3418699f86a4fe5c1d1ab93c141\trefs/heads/develop\n"})
          (is (sub? {:git/sha "24d55f3165ada3418699f86a4fe5c1d1ab93c141"
                     :git/ref "refs/heads/develop"}
                    (:git/version (sut/resolve-version
                                   [:dep/git "https://github.com/seancorfield/next-jdbc" "develop"])))))
        (testing "a known partial SHA"
          (mock-bb-process! '(["git" "ls-remote" "--symref" "https://:@github.com/seancorfield/next-jdbc" "24d55f31"])
                            ^:template/not-found {})
          (mock-gitlib-resolve! "24d55f3165ada3418699f86a4fe5c1d1ab93c141")
          (is (sub? {:git/sha "24d55f3165ada3418699f86a4fe5c1d1ab93c141"}
                    (:git/version (sut/resolve-version
                                   [:dep/git "https://github.com/seancorfield/next-jdbc" "24d55f31"])))))

        (testing "remote SHA does not exist"
          (mock-bb-process! '(["git" "ls-remote"])
                            ^:template/offline {})
          (mock-gitlib-resolve! nil)
          (is (sub? {:error/id :resolve.git/sha-not-found}
                    (:error (sut/resolve-version
                             [:dep/git "https://github.com/seancorfield/next-jdbc" "abc123"])))))
        (testing "remote repo does not exist"
          (mock-bb-process! '(["git" "ls-remote"])
                            ^:template/offline {})
          (mock-gitlib-resolve! {:cause "Unable to clone ,,," :err "could not read Username"})
          (is (sub? {:error/id :resolve.git/repos-not-found}
                    (:error (sut/resolve-version
                             [:dep/git "https://github.com/seancorfield/next-jdbc2" "abc123"])))))
        (testing "offline and SHA not locally"
          (mock-bb-process! '(["git" "ls-remote"])
                            ^:template/offline {})
          (mock-gitlib-resolve! {:cause "Unable to fetch" :err "Could not resolve host"})
          (is (sub? {:error/id :resolve.git/sha-not-found-offline}
                    (:error (sut/resolve-version
                             [:dep/git "https://github.com/seancorfield/next-jdbc" "abc123"])))))
        (testing "offline and repo not locally"
          (mock-bb-process! '(["git" "ls-remote"])
                            ^:template/offline {})
          (mock-gitlib-resolve! {:cause "Unable to clone" :err "Could not resolve host"})
          (is (sub? {:error/id :resolve.git/repos-not-found-offline}
                    (:error (sut/resolve-version
                             [:dep/git "https://github.com/foo/bar" "abc123"])))))))))

(comment

  (with-redefs [clojure.tools.gitlibs.config/CONFIG
                (delay (update (@(requiring-resolve 'clojure.tools.gitlibs.config/init-config))
                               :gitlibs/dir (constantly (str (fs/path (io/resource "gitlibs"))))))]

    ((requiring-resolve 'clojure.tools.gitlibs/resolve) "https://github.com/seancorfield/next-jdb2" "abc123"))
  *e
  (ex-data *e)

  (throw (ex-info "Unable to fetch /Users/gert/projects/deps-try/deps-try/resources/gitlibs/_repos/https/github.com/seancorfield/next-jdbc\nfatal: unable to access 'https://github.com/seancorfield/next-jdbc/': Could not resolve host: github.com"
                  {:args '("git" "--git-dir" "/Users/gert/.gitlibs/_repos/https/github.com/seancorfield/next-jdbc" "fetch" "--quiet" "--all" "--tags" "--prune"), :exit 128, :out nil, :err "fatal: unable to access 'https://github.com/seancorfield/next-jdbc.git/': Could not resolve host: github.com"}))
  (Throwable->map *e)
  (Exception. "Some msg")
  (deref (requiring-resolve 'clojure.tools.gitlibs.config/CONFIG))

  (select-keys @((requiring-resolve 'babashka.process/process) ["git" "ls-remote" "--symref" "https://:@github.com/seancorfield/next-jdbc" "24d55f31"] {:out :string :err :string}) [:out :exit :err])

  #_:end)

(deftest format-error-test
  (is (= "Could not find library 'foo/bar' on Maven Central or Clojars."
         (sut/format-error {:error/id :resolve.mvn/library-not-found :lib "foo/bar"}))))
