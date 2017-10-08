(def project 'sidewalk-clj)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"resources" "src"}
          :dependencies '[[org.clojure/clojure "RELEASE"]
                          [org.clojure/core.async "0.3.443"]
                          [clj-opc "0.1.1"]
                          [mount "0.1.11"]
                          [com.rpl/specter "1.0.3"]])

(task-options!
 aot {:namespace #{'sidewalk-clj.core}}
 pom {:project project
      :version version
      :license {:name "MIT License"
                :url "https://opensource.org/licenses/MIT"}}
 jar {:main 'sidewalk-clj.core
      :file (str "sidewalk-clj-" version "-standalone.jar")})

(deftask build
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  (let [dir (if (seq dir) dir #{"target"})]
    (comp (aot) (pom) (uber) (jar) (target :dir dir))))

(deftask run
  "Run the project."
  [a args ARG [str] "the arguments for the application."]
  (require '[sidewalk-clj.core :as app])
  (apply (resolve 'app/-main) args))

(deftask dev 
  "Run a development REPL"
  []
  (repl :init-ns 'sidewalk-clj.core))
