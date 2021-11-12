#!/usr/bin/env bb

(ns test-native
  (:require [clojure.java.io :as io]
            [helper.env :as env]
            [helper.fs :as fs]
            [helper.graal :as graal]
            [helper.main :as main]
            [helper.shell :as shell]
            [lread.status-line :as status]))

(defn generate-test-runner [dir]
  (status/line :head "Generate test runner")
  (fs/delete-file-recursively dir true)
  (io/make-parents dir)
  (shell/command "clojure" "-M:script:test-common"
                 "-m" "clj-graal.gen-test-runner"
                 "--dest-dir" dir "test-by-namespace"))

(defn -main [& args]
  (when (main/doc-arg-opt args)
    (let [native-image-xmx "6g"
          target-path "target"
          target-exe "rewrite-clj-test"
          full-target-exe (str target-path "/" target-exe (when (= :win (env/get-os)) ".exe"))]
      (status/line :head "Creating native image for test")
      (status/line :detail "java -version")
      (shell/command "java -version")
      (status/line :detail (str "\nnative-image max memory: " native-image-xmx))
      (let [graal-native-image (graal/find-graal-native-image)
            test-runner-dir "target/generated/graal"]
        (graal/clean)
        (generate-test-runner test-runner-dir)
        (let [classpath (graal/compute-classpath "test-common:graal:native-test")]
          (graal/aot-compile-sources classpath "clj-graal.test-runner")
          (graal/run-native-image {:graal-native-image graal-native-image
                                   :target-path target-path
                                   :target-exe target-exe
                                   :classpath classpath
                                   :native-image-xmx native-image-xmx
                                   :entry-class "clj_graal.test_runner"})))
      (status/line :head "Native image built")
      (status/line :detail "built: %s, %d bytes" full-target-exe (.length (io/file full-target-exe)))
      (status/line :head "Running tests natively")
      (shell/command full-target-exe)))
  nil)

(main/when-invoked-as-script
 (apply -main *command-line-args*))
