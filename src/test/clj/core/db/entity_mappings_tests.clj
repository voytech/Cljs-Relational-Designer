(ns core.db.entity-mappings-tests
  (:require [clojure.test :refer :all]
            [core.db.entities :refer :all]
            [core.db.manager :refer :all]
            [datomic.api :as d]))

(defn- h2-db-url []
  (str (:db-url (load-configuration "resources/schema/properties.edn")) "/testing"))

(defn- mem-db-url []
  (println (:db-url (load-configuration "resources/schema/test_properties.edn")))
  (:db-url (load-configuration "resources/schema/test_properties.edn")))

(defn- init-in-memory-db []
  (initialize-database (mem-db-url) (load-file "resources/schema/public_schema.edn")))

(defn- find-property-named [prop-name dburl]
  (let [db (d/db (d/connect dburl))]
    (:db/ident (first (first (d/q '[:find (pull ?p [:db/ident])
                                    :in $ ?name
                                    :where [?p :db/ident ?name]] db prop-name))))))

(deftest test-defentity-macro
  (defschema {:mapping-inference true
              :auto-persist-schema true
              :db-url (mem-db-url)}
    (defentity 'user.login
      (property name :username  type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password  type :db.type/string                            mapping-opts {:required true})
      (property name :roles     type :db.type/ref                               mapping-opts {:required true})
      (property name :tenant    type :db.type/ref                               mapping-opts {:lookup-ref #([:user.login/username %])}))
    (defentity 'tenant.login
      (property name :username      type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password      type :db.type/string                            mapping-opts {:required true})
      (property name :dburl         type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :organization  type :db.type/string unique :db.unique/identity mapping-opts {:required true})))
  (is (= :user.login/username (find-property-named :user.login/username (mem-db-url))))
  (is (= :user.login/password (find-property-named :user.login/password (mem-db-url))))
  (is (= :user.login/roles (find-property-named :user.login/roles (mem-db-url))))
  (is (= :user.login/tenant (find-property-named :user.login/tenant (mem-db-url))))
  (println (get-frequencies))
  (is (not (nil? (get-frequencies)))))

(deftest test-resolve-mapping
  (defschema {:mapping-inference true
              :auto-persist-schema true
              :db-url (mem-db-url)}
    (defentity 'user.login
      (property name :username  type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password  type :db.type/string                            mapping-opts {:required true})
      (property name :roles     type :db.type/ref                               mapping-opts {:required true})
      (property name :tenant    type :db.type/ref                               mapping-opts {:lookup-ref #([:user.login/username %])}))
    (defentity 'tenant.login
      (property name :username      type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password      type :db.type/string                            mapping-opts {:required true})
      (property name :dburl         type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :organization  type :db.type/string unique :db.unique/identity mapping-opts {:required true})))

  (let [entity {:username "Wojtek"
                :password "sdakdshd"
                :dburl    "localhost:432"}]
    (is (= 'tenant.login (-> (find-mapping entity)
                             (:type))))))

(deftest test-cannot-resolve-mapping
  (defschema {:mapping-inference true
              :auto-persist-schema true
              :db-url (mem-db-url)}
    (defentity 'user.login
      (property name :username  type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password  type :db.type/string                            mapping-opts {:required true})
      (property name :roles     type :db.type/ref                               mapping-opts {:required true})
      (property name :tenant    type :db.type/ref                               mapping-opts {:lookup-ref #([:user.login/username %])}))
    (defentity 'tenant.login
      (property name :username      type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password      type :db.type/string                            mapping-opts {:required true})
      (property name :dburl         type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :organization  type :db.type/string unique :db.unique/identity mapping-opts {:required true})))
  (let [entity {:username "Wojtek"
                :password "asdjkhasd"}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Cannot determine mapping. At least two mappings with same frequency"
                          (find-mapping entity)))))

(deftest test-map-entity-via-mapping-def
  (defschema {:mapping-inference true
              :auto-persist-schema true
              :db-url (mem-db-url)}
    (defentity 'user.login
      (property name :username  type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password  type :db.type/string                            mapping-opts {:required true})
      (property name :roles     type :db.type/ref                               mapping-opts {:required true})
      (property name :tenant    type :db.type/ref                               mapping-opts {:lookup-ref (fn [v] [:user.login/username v])}))
    (defentity 'tenant.login
      (property name :username      type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :password      type :db.type/string                            mapping-opts {:required true})
      (property name :dburl         type :db.type/string unique :db.unique/identity mapping-opts {:required true})
      (property name :organization  type :db.type/string unique :db.unique/identity mapping-opts {:required true})))
  (let [entity {:username "Wojtek"
                :password "sdasdjhg"
                :dburl    "localhost:432"}
        entity1 {:username "wojciech"
                 :password "tdsadsa"
                 :tenant "empik-photo"}]
    ;(println (clj->db entity))
    ;(println (clj->db entity1))
    ))

;; (deftest test-map-entity-vec-via-mapping-def

;;  (init {:mapping-inference true
;;          :auto-persist-schema true
;;          :db-url (mem-db-url)}
;;        (defentity 'user.login
;;             (property name :username  type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :password  type :db.type/string                            mapping-opts {:required true})
;;             (property name :roles     type :db.type/ref                               mapping-opts {:required true})
;;             (property name :tenant    type :db.type/ref                               mapping-opts {:lookup-ref (fn [v] [:user.login/username v])}))
;;        (defentity 'tenant.login
;;             (property name :username      type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :password      type :db.type/string                            mapping-opts {:required true})
;;             (property name :dburl         type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :organization  type :db.type/string unique :db.unique/identity mapping-opts {:required true})))
;;   (let [entity-vec [{:username "Wojtek"
;;                      :password "asdasdasd"
;;                      :dburl    "localhost:432"}
;;                     {:username "Jack"
;;                      :password "Jack1"
;;                      :dburl    "jack.com"}]]
;;     (println (map-entity entity-vec))
;;     ))

;; (deftest test-map-entity-mapping-opts-rel-via-mapping-def
;;   (init {:mapping-inference true
;;          :auto-persist-schema true
;;          :db-url (mem-db-url)}
;;        (defentity 'user.login
;;             (property name :username  type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :password  type :db.type/string                            mapping-opts {:required true})
;;             (property name :roles     type :db.type/ref                               mapping-opts {:required true})
;;             (property name :tenant    type :db.type/ref                               mapping-opts {:lookup-ref (fn [v] [:user.login/username v])}))
;;        (defentity 'tenant.login
;;             (property name :username      type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :password      type :db.type/string                            mapping-opts {:required true})
;;             (property name :dburl         type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :users         type :db.type/ref                               mapping-opts {:ref-type 'user.login})
;;             (property name :organization  type :db.type/string unique :db.unique/identity mapping-opts {:required false})))
;;     (let [entity-vec {:username "Wojtek"
;;                     :password "adasd"
;;                     :dburl    "localhost:432"
;;                     :users [{:username "Jack"
;;                              :password "Jack1"
;;                              :roles "USER"
;;                              :tenant "Wojtek"}
;;                             {:username "tedd"
;;                              :password "tedd1"
;;                              :roles "USER"
;;                              :tenant "Wojtek"}]}]
;;     (println (map-entity entity-vec))
;;     ))

;; (deftest test-mapping-forth-and-back-1 []
;;   (defschema {:mapping-inference true
;;               :auto-persist-schema true
;;               :db-url (mem-db-url)}
;;        (defentity 'user.login
;;             (property name :username  type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :password  type :db.type/string                            mapping-opts {:required true})
;;             (property name :roles     type :db.type/ref                               mapping-opts {:required true})
;;             (property name :tenant    type :db.type/ref                               mapping-opts {:lookup-ref (fn [v] [:user.login/username v])}))
;;        (defentity 'tenant.login
;;             (property name :username      type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :password      type :db.type/string                            mapping-opts {:required true})
;;             (property name :dburl         type :db.type/string unique :db.unique/identity mapping-opts {:required true})
;;             (property name :users         type :db.type/ref                               mapping-opts {:ref-type 'user.login})
;;             (property name :organization  type :db.type/string unique :db.unique/identity mapping-opts {:required false})))
;;     (let [entity-vec {:username "Wojtek"
;;                       :password "adasd"
;;                       :dburl    "localhost:432"}
;;           db (clj->db entity-vec)
;;           clj (db->clj db)]
;;     (println "Mapping forth ========================================")
;;     (println db)
;;     (println "Mapping back  ========================================")
;;     (println clj)))
