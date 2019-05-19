(ns cljs-diagrams.core.nodes
  (:require [cljs-diagrams.core.eventbus :as bus]
            [cljs-diagrams.core.shapes :as d]
            [cljs-diagrams.core.state :as state]
            [clojure.spec.alpha :as spec]
            [cljs-diagrams.core.utils.general :as utils :refer [make-js-property]]))

(spec/def ::node (spec/keys :req-un [::uid
                                     ::bbox
                                     ::type
                                     ::tags
                                     ::shapes
                                     ::relationships
                                     ::layouts
                                     ::shapes-properties]))

(spec/def ::create-node (spec/keys :req-un [::bbox
                                            ::type
                                            ::tags
                                            ::shapes-properties]))

(declare get-node-shape)

(defn- assert-keyword [tokeyword]
  (if (keyword? tokeyword) tokeyword (keyword tokeyword)))

(defn shapes-of [node]
 (vals (:shapes node)))

(defn node-by-id [app-state id]
 (state/get-in-diagram-state app-state [:nodes id]))

(defn reload [app-state node]
  (node-by-id app-state (:uid node)))

(defn is-node [target]
  (spec/valid? ::node target))

;;Utility functions for getting expected data on type non-deterministic argument
(defn- id [input fetch]
  (cond
    (is-node input) (fetch input)
    (string? input) input))

(defn- record [input fetch]
  (cond
    (is-node input) input
    (string? input) (fetch input)))

(defn- node-id [input]
  (id input :uid))

(defn- node-record [app-state input]
  (record input #(node-by-id app-state %)))

(defn volatile-node [app-state node]
  (->> node
       node-id
       (node-record app-state)))

(defn- shape-id [input]
  (id input :name))

(defn- shape-record [app-state input node]
  (let [nodes (state/get-in-diagram-state app-state [:nodes])]
    (record input #(get-in nodes [(node-id node) :shapes %]))))

(defn lookup [app-state shape]
  (let [uid (:parent-ref shape)]
    (node-by-id app-state uid)))

(defn create-node
  "Creates editable node. Node is a first class entity.
   Node consists of shapes which are its building blocks. Shapes defines drawable properties which can interact with
   each other within node. Shape adds properties (or hints) wich holds state and allow to implement different behaviours."
  ([app-state type tags bbox shapes-properties]
   (let [uid (str (random-uuid))
         node   {:uid uid
                 :bbox bbox
                 :type type
                 :tags tags
                 :shapes {}
                 :relationships []
                 :layouts {}
                 :shapes-properties shapes-properties}
         new-state (state/assoc-diagram-state app-state [:nodes uid] node)]
     {:new-state (bus/fire new-state "node.added" {:node node})
      :target node}))
  ([app-state type bbox]
   (create-node app-state type [] bbox {})))

(defn remove-node [app-state node]
  (state/dissoc-diagram-state app-state [:nodes (:uid node)]))

(defn set-bbox [app-state node bbox]
  (let [new-state (state/assoc-diagram-state app-state [:nodes (:uid node) :bbox] bbox)]
    {:new-state new-state
     :target (node-by-id new-state (:uid node))}))

(defn add-node-shape [app-state node shape-data]
   (let [{:keys [new-state target]} (d/new-shape app-state node shape-data)]
     (let [new-state (bus/fire new-state "node.shape.added" {:node (node-by-id new-state (:uid node))})]
       {:new-state new-state
        :target (node-by-id new-state (:uid node))})))

(defn remove-node-shape [app-state node shape-name]
  (state/dissoc-diagram-state app-state [:nodes (:uid node) :shapes shape-name]))

(defn remove-node-shapes [app-state node pred]
  (let [all (shapes-of (node-by-id app-state (:uid node)))
        filtered-out (filterv pred all)]
    (reduce (fn [imm-app-state shape] (remove-node-shape imm-app-state node (:name shape)))
            app-state
            filtered-out)))

(defn update-shape-attribute [app-state node name attribute value]
 (state/assoc-diagram-state app-state [:nodes (:uid node) :shapes name :attributes attribute] value))

(defn remove-shape-attribute [app-state node name attribute]
 (state/dissoc-diagram-state app-state [:nodes (:uid node) :shapes name :attributes attribute]))

(defn shape-attribute [app-state node name attribute]
  (state/get-in-diagram-state app-state [:nodes (:uid node) :shapes name :attributes attribute]))

(defn preset-shapes-properties [node name]
  (get-in node [:shapes-properties name]))

(defn set-shape-layout-hint [app-state shape hint value]
  (state/assoc-diagram-state app-state [:nodes (:parent-ref shape)
                                        :shapes (:name shape)
                                        :layout-attributes
                                        :layout-hints
                                        hint] value))

(defn set-shape-layout-attributes [app-state shape layout-attributes]
  (state/assoc-diagram-state app-state [:nodes (:parent-ref shape)
                                        :shapes (:name shape)
                                        :layout-attributes] layout-attributes))

(defn shape-layout-attributes [app-state shape]
  (state/get-in-diagram-state app-state [:nodes (:parent-ref shape)
                                         :shapes (:name shape)
                                         :layout-attributes]))

(defn get-node-shape
 ([node name-or-type]
  (if (keyword? name-or-type)
   (filter #(= name-or-type (:type %)) (shapes-of node))
   (get-in node [:shapes name-or-type])))
 ([app-state node name-or-type]
  (get-node-shape (node-by-id app-state (:uid node)) name-or-type)))

(defn assert-shape
 ([func app-state node name data]
  (let [node (node-by-id app-state (:uid node))
        shape (get-node-shape node name)
        new-state (if (nil? shape)
                    (func app-state node {:name name :model data})
                    (d/set-data app-state shape data))]
    {:target (get-node-shape new-state node name)
     :new-state new-state}))
 ([func app-state node args-map]
  (let [node (node-by-id app-state (:uid node))
        shape (get-node-shape node (:name args-map))
        new-state (if (nil? shape)
                    (func app-state node args-map)
                    (d/set-data app-state shape (:model args-map)))]
    {:target (get-node-shape new-state node (:name args-map))
     :new-state new-state})))

(defn add-layout
  ([app-state node layout]
   (let [uid (:uid node)
         new-state (state/assoc-diagram-state app-state [:nodes uid :layouts (:name layout)] layout)
         updated (node-by-id new-state uid)]
     {:new-state (bus/fire new-state "node.layout.added" {:node updated})
      :target updated}))
  ([app-state node name layout-func position size margins]
   (let [layout (l/layout name layout-func position size margins)
         new-state (state/assoc-diagram-state app-state [:nodes (:uid node) :layouts (:name layout)] layout)
         updated (node-by-id new-state (:uid node))]
     {:new-state (bus/fire new-state "node.layout.added" {:node updated})
      :target updated}))
  ([app-state node name layout-func position margins]
   (add-layout app-state node name layout-func position (l/match-parent-size) margins))
  ([app-state node name layout-func position]
   (add-layout app-state node name layout-func position (l/match-parent-size) nil))
  ([app-state node name layout-func]
   (add-layout app-state node name layout-func (l/match-parent-position) (l/match-parent-size) margins)))

(defn remove-layout [app-state node layout-name]
  (state/dissoc-diagram-state app-state [:nodes (:uid node) :layouts layout-name]))

(defn get-layout [app-state node layout-name]
  (state/get-in-diagram-state app-state [:nodes (:uid node) :layouts layout-name]))

(defn assert-layout [app-state node name layout-func position size margins]
  (let [layout (get-layout app-state node name)]
    (if (nil? layout)
      (add-layout app-state node name layout-func position size margins)
      (let [modified (-> layout
                         (assoc :position position)
                         (assoc :size size)
                         (assoc :margins margins)
                         (assoc :layout-func layout-func))]
        (state/assoc-diagram-state app-state [:nodes (:uid node) :layouts name] modified)))))

(defn- is-relation-present [app-state node related-id assoc-type]
  (->> (node-by-id app-state (:uid node))
       :relationships
       (filterv (fn [rel] (and (= related-id (:node-id rel)) (= assoc-type (:relation-type rel)))))
       (count)
       (< 0)))

(defn connect-nodes [app-state src trg association-type]
  (when (not (is-relation-present app-state src (:uid trg) association-type))
    (let [src-rel (conj (:relationships src) {:relation-type association-type :node-id (:uid trg) :owner (:uid src)})
          trg-rel (conj (:relationships trg) {:relation-type association-type :node-id (:uid src) :owner (:uid src)})]
      (-> app-state
          (state/assoc-diagram-state [:nodes (:uid src) :relationships] src-rel)
          (state/assoc-diagram-state [:nodes (:uid trg) :relationships] trg-rel)))))

(defn get-related-nodes [app-state node association-type]
  (let [node (volatile-node app-state node)]
    (->> (:relationships node)
         (filter  #(= (:relation-type %) association-type))
         (mapv #(node-by-id app-state (:node-id %))))))

(defn disconnect-nodes
  ([app-state src trg]
   (let [src-rel (filter #(not= (:uid trg) (:node-id %)) (:relationships src))
         trg-rel (filter #(not= (:uid src) (:node-id %)) (:relationships trg))]
     (-> app-state
         (state/assoc-diagram-state [:nodes (:uid src) :relationships] src-rel)
         (state/assoc-diagram-state [:nodes (:uid trg) :relationships] trg-rel))))
  ([app-state src trg association-type]
   (let [src-rel (filter #(and (not= (:relation-type %) association-type)
                               (not= (:uid trg) (:node-id %))) (:relationships src))
         trg-rel (filter #(and (not= (:relation-type %) association-type)
                               (not= (:uid src) (:node-id %))) (:relationships trg))]
     (-> app-state
         (state/assoc-diagram-state [:nodes (:uid src) :relationships] src-rel)
         (state/assoc-diagram-state [:nodes (:uid trg) :relationships] trg-rel)))))