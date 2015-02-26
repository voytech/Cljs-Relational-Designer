(set-env!
  :project      'photo-collage
  :version      "0.1.0-SNAPSHOT"
  :dependencies '[
                  [tailrecursion/hoplon      "6.0.0-SNAPSHOT"]
                  [tailrecursion/boot-hoplon "0.1.0-SNAPSHOT" :scope "test"]
                  [tailrecursion/javelin     "3.7.2"]
                  [io.hoplon.vendor/jquery   "2.1.1-0"]
                  [com.cemerick/clojurescript.test "0.3.3"]
                  [io.hoplon/twitter.bootstrap "0.2.0"]]
  :out-path     "resources/public"
  :source-paths    #{"src"})


(require
  '[tailrecursion.boot-hoplon :refer :all]
  '[cemerick.cljs.test :refer :all]
  )

(deftask development
  "Build photo-collage for development."
  []
  (comp (watch) (hoplon {:pretty-print true :prerender false}) ;;(dev-server :port 3333)
        ))

(deftask dev-debug
  "Build photo-collage for development with source maps."
  []
  (comp (watch) (hoplon {:pretty-print true
                         :prerender false
                         :source-map true}) ;;(dev-server :port 3333)
        ))

(deftask production
  "Build photo-collage for production."
  []
  (hoplon {:optimizations :advanced}))


(deftask cljs-test
  "Run clojurescript.test tests"
  []
)
