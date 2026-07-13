(ns eval.deps-try.util.jvmti
  "This namespace contains code exclusive to JDK9+ and should not be attempted to
  load with earlier JDKs."
  (:require
   [clojure.java.io :as io])
  (:import
   (com.sun.tools.attach VirtualMachine)
   (java.lang ProcessHandle)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (dt JvmtiAgent)))

;;; Agent unpacking

(defonce ^:private temp-directory
  ;; deleteOnExit runs in reverse order of registration: the unpacked lib
  ;; (registered later) is deleted first, leaving the directory empty.
  (doto (.toFile (Files/createTempDirectory "deps_try" (into-array FileAttribute [])))
    (.deleteOnExit)))

(defn- unpack-from-jar [resource-name]
  (let [path (io/file temp-directory resource-name)]
    (if-let [resource (io/resource resource-name)]
      (with-open [in (io/input-stream resource)]
        (io/copy in path))
      (throw (ex-info (str "Could not find " resource-name " in resources.") {})))
    (.deleteOnExit path)
    (.getAbsolutePath path)))

(defn- macos? []
  (re-find #"(?i)mac" (System/getProperty "os.name")))

(defn- linux? []
  (re-find #"(?i)linux" (System/getProperty "os.name")))

(defn- aarch64? []
  (re-find #"(?i)aarch64" (System/getProperty "os.arch")))

(def ^:private libdt-path
  (delay
    (let [os  (System/getProperty "os.name")
          lib (cond (macos?)                  "libdt-macos-universal.so"
                    (and (linux?) (aarch64?)) "libdt-linux-arm64.so"
                    (linux?)                  "libdt-linux-x64.so"
                    :else                     (throw (ex-info (str "no native agent bundled for " os
                                                                   " (only Linux and macOS)") {:os os})))]
      (unpack-from-jar lib))))

;;; Agent loading

(defn- attach-self ^VirtualMachine []
  (VirtualMachine/attach (str (.pid (ProcessHandle/current)))))

(defn- load-libdt-agent []
  (doto (attach-self)
    (.loadAgentPath @libdt-path)
    (.detach)))

(def ^:private agent-loaded (delay (load-libdt-agent)))

(defn stop-thread
  "Stop the given `thread` using JVMTI StopThread function. Risks state
  corruption. Should not be used prior to JDK20."
  [thread]
  @agent-loaded
  (JvmtiAgent/stopThread thread))
