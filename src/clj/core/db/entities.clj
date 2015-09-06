(ns core.db.entities
  (:require [datomic.api :as d]))

; Below mapping should be defined using concise macro defined api as follows:
; (defentity user-login
;   (from :username to :user/name     with {:required true, unique true})
;   (from :password to :user/password with {:required true})
;   (from :roles    to :user/roles    with {:required true})
;   (from :tenant   to :user/tenant   with {:lookup-ref #([:user/name %])})
;   (from :messages to :user/message  with {:relation {:type 'message}
;                                           }

; Above defentity is a macro and from is also macro which should be expanded.
; Entity will add datomic attribute :shared/type representing type of mapping, when pulling entities.
(declare map-entity)
(def user-login {
                 :type :user-login,
                 :mapping {
                                :username {:key :user/name,
                                           :with {:required true}},
                                :password {:key :user/password,
                                           :with {:required true}},
                                :roles    {:key  :user/roles,
                                           :with {:required true}},
                                :tenant   {:key :user/tenant,
                                           :with {:lookup-ref #([:user/name %])}}}

                                :user/name     {:key :username},
                                :user/password {:key :password}
                 })

(defn mapping-into-ns [mapping-symbol]
  (symbol (str "mappings.runtime/" (name mapping-symbol))))

(defn var-by-symbol [symbol]
  (-> symbol resolve var-get))

(defn inject-def [ns var-name value]
 (intern ns var-name value))

(defn entities-frequencies []
  (var-by-symbol 'mappings.runtime/entities-frequencies))

(defn inject-dynamic-def [ns var-name value]
  (.setDynamic (intern ns var-name value)))


(defn- validate [property-1 direction property-2 with opts-map]
  (when (not (keyword? property-1))  (throw (IllegalArgumentException. "first arg must be keyword")))
  (when (not (symbol? direction))    (throw (IllegalArgumentException. "second argument must be symbol 'to'")))
  (when (not (keyword? property-2))  (throw (IllegalArgumentException. "third argument must be keyword")))
  (when (not (symbol? with))         (throw (IllegalArgumentException. "fourth argument must be symbol"))))

(defmacro from [property to attribute with opts-map]
     (validate property to attribute with opts-map)
    `{~property {:key ~attribute, :with ~opts-map}  ;(merge {:key ~attribute} ~opts-map)
      })

(defn concat-into [& items]
  (into [] (apply concat items)))

;TODO: create mapping-type attribute for datomic. When saving entity pass this mapping-type so that unmapping can be done.
(defmacro defentity [entity-name & rules]
  `(do (create-ns 'mappings.runtime)
       (let [entity-var# (var-get (intern 'mappings.runtime ~entity-name
                                          {:type (symbol (str "mappings.runtime/" (name ~entity-name)))
                                           :mapping (apply merge (map #(macroexpand-1 %) (list ~@rules))) ; I think map and macroexpand is not needed.
                                           }
                                          ))]
         (when (:mapping-detection (var-by-symbol 'mappings.runtime/mapping-opts))
           (let [prop-map# (apply merge (mapv (fn [k#] {k# [(:type entity-var#)]}) (-> entity-var# :mapping (keys))))]
              (swap! (var-by-symbol 'mappings.runtime/*entities-frequencies*) (partial merge-with concat-into) prop-map#)))
         entity-var#
         )))

(defmacro init [opts & defentities]
  `(do (create-ns 'mappings.runtime)
       (inject-def 'mappings.runtime '~'mapping-opts ~opts)
       (inject-def 'mappings.runtime '~'*entities-frequencies* (atom {}))
       ~@defentities
       (->> @(var-by-symbol 'mappings.runtime/*entities-frequencies*)
             (inject-def 'mappings.runtime '~'entities-frequencies))
       ))

(defn mapping-by-symbol [symbol]
  (-> symbol resolve var-get))

(defn find-mapping [service-entity]
  (let [freqs (->> (mapv #(get (var-by-symbol 'mappings.runtime/entities-frequencies) %) (keys service-entity))
                   (apply concat)
                   (frequencies))
        freqs-vec (mapv (fn [k] {:k k, :v (get freqs k)}) (keys freqs))
        max-val (apply max (mapv #(get freqs %) (keys freqs)))
        max-entries (filter #(= max-val (:v %)) freqs-vec)]
     (when (< 1 (count max-entries)) (throw (ex-info "Cannot determine mapping. At least two mappings with same frequency" {:entries max-entries})))
     (-> (first max-entries)
         :k
         (var-by-symbol))
    ))

(defmulti map-property-disp :conf)

(defmethod map-property-disp :lookup-ref [{:keys [type conf conf-value source-entity target-property source-property-value]}]
  (swap! source-entity assoc target-property (conf-value source-property-value)))

(defmethod map-property-disp :relation [{:keys [type conf conf-value source-entity target-property source-property-value]}]
    (swap! source-entity assoc target-property (map-entity source-property-value
                                                           (var-by-symbol (-> conf-value
                                                                              :type)))))

(defmethod map-property-disp :default [{:keys [type conf conf-value source-entity target-property source-property-value]}]
  (swap! source-entity assoc target-property source-property-value))

(defn- map-property [type temp-source target-property value with]
  (doseq [with-key (keys with)]
     (map-property-disp {
                         :type type
                         :conf with-key
                         :conf-value (get with with-key)
                         :source-entity temp-source
                         :target-property target-property
                         :source-property-value value})
    ))

(defmulti map-entity (fn ([source mapping] (type source))
                         ([source] (type source))))

(defmethod map-entity (type {})
  ([source mapping]
   (let [source-props (keys source)
         target-props (:mapping mapping)
         temp-source (atom source)]
     (doall (map #(do  (map-property (:type mapping)
                                     temp-source
                                     (:key (% target-props))
                                     (% source)
                                     (:with (% target-props)))
                       (swap! temp-source dissoc %)) source-props))
     @temp-source))
  ([source]
   (map-entity source (find-mapping source))))

(defmethod map-entity (type [])
  ([source mapping]
   (mapv #(map-entity % mapping) source))
  ([source]
   (map-entity source (find-mapping (first source))))) ;;Use first entity in the vector to determine mapping.
