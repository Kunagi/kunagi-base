(ns kunagi-base.assets.loader
  (:require
   [clojure.edn :as edn]
   [clojure.java.shell :as shell]))


(defn- git-pull [repo-path]
  (let [result (shell/sh "git" "pull" "--ff-only" :dir repo-path)]
    (if-not (= 0 (-> result :exit))
      (tap> [:wrn ::git-pull-failed result]))))


(defn default-load-f [db asset-pool asset-path]
  (let [dir-path (-> asset-pool :asset-pool/dir-path)
        git-repo? (-> asset-pool :asset-pool/git-repo?)
        file-path (str dir-path "/" asset-path)
        file (java.io.File. file-path)]
    (when git-repo?
      (git-pull dir-path))
    (when (.exists file)
      (if (.endsWith file-path ".edn")
        (edn/read-string (slurp file))
        file))))
