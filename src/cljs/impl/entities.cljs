(ns impl.entities
 (:require [core.entities :as e]
           [core.project :as p]
           [core.entities-behaviours :as eb :refer [endpoint moving-entity highlight relation-line
                                                    insert-breakpoint dissoc-breakpoint all moving-endpoint
                                                    intersects? intersects-any? toggle-endpoints show
                                                    position-endpoint relations-validate event-wrap arrow]]
           [clojure.string :as str])
 (:require-macros [core.macros :refer [defentity]]))


(defentity rectangle-node data options
  (with-drawables
    (let [enriched-opts (merge options
                               eb/DEFAULT_SIZE_OPTS
                               eb/TRANSPARENT_FILL
                               eb/DEFAULT_STROKE
                               eb/RESTRICTED_BEHAVIOUR
                               eb/NO_DEFAULT_CONTROLS)
          conL    (vector (:left options) (+ (/ (:height eb/DEFAULT_SIZE_OPTS) 2) (:top options)))
          conR    (vector (+ (:left options) (:width eb/DEFAULT_SIZE_OPTS)) (+ (/ (:height eb/DEFAULT_SIZE_OPTS) 2) (:top options)))
          conT    (vector (+ (/ (:width eb/DEFAULT_SIZE_OPTS) 2) (:left options)) (:top options))
          conB    (vector (+ (/ (:width eb/DEFAULT_SIZE_OPTS) 2) (:left options)) (+ (:top options) (:height eb/DEFAULT_SIZE_OPTS)))]
      [{:name "connector-left"
        :type :endpoint
        :src (endpoint conL :moveable false :display "rect" :visibile false)
        :behaviours {"mouse:over" (event-wrap show true)
                     "mouse:out"  (event-wrap show false)}}
       {:name "connector-right"
        :type :endpoint
        :src (endpoint conR :moveable false :display "rect" :visibile false)
        :behaviours {"mouse:over" (event-wrap show true)
                     "mouse:out"  (event-wrap show false)}}
       {:name "connector-top"
        :type :endpoint
        :src (endpoint conT :moveable false :display "rect" :visibile false)
        :behaviours {"mouse:over" (event-wrap show true)
                     "mouse:out"  (event-wrap show false)}}
       {:name "connector-bottom"
        :type :endpoint
        :src (endpoint conB :moveable false :display "rect" :visibile false)
        :behaviours {"mouse:over" (event-wrap show true)
                     "mouse:out"  (event-wrap show false)}}
       {:name "body"
        :type :main
        :src (js/fabric.Rect. (clj->js enriched-opts))
        :behaviours {"object:moving" (moving-entity "body")
                     "mouse:over"    (highlight true eb/DEFAULT_OPTIONS)
                     "mouse:out"     (highlight false eb/DEFAULT_OPTIONS)}}])))



(defentity relation data options
  (with-drawables
    (let [enriched-opts (merge options eb/DEFAULT_SIZE_OPTS eb/DEFAULT_STROKE eb/RESTRICTED_BEHAVIOUR eb/NO_DEFAULT_CONTROLS)
          offset-x (:left options)
          offset-y (:top options)
          points-pairs (partition 2 data)
          points-pairs-offset (map #(vector (+ (first %) offset-x) (+ (last %) offset-y)) points-pairs)
          conS (first points-pairs-offset)
          conE (last points-pairs-offset)]
        [{:name "connector"
          :type :relation
          :src  (relation-line (first conS) (last conS) (first conE) (last conE) enriched-opts)
          :behaviours {"mouse:up" (insert-breakpoint)
                       "object:moving" (all (moving-entity "connector")
                                            (event-wrap relations-validate))}
          :rels {:start "start" :end "end"}}

         {:name "start"
          :type :startpoint
          :src  (endpoint conS :moveable true :display "circle" :visible true :opacity 1)
          :behaviours {"object:moving" (all (moving-endpoint)
                                            (intersects? "body" (fn [src trg] (toggle-endpoints (:entity trg) true))
                                                                (fn [src trg] (toggle-endpoints (:entity trg) false))))
                       "mouse:up"      (all (intersects-any? #{"connector-top" "connector-bottom" "connector-left" "connector-right"} (fn [src trg] (e/connect-entities (:entity src) (:entity trg) (:drawable src))
                                                                                                                                                    (toggle-endpoints (:entity trg) false)
                                                                                                                                                    (position-endpoint (:entity src) "start" (.-left (:src trg)) (.-top (:src trg)))))
                                            (event-wrap relations-validate))
                       "mouse:over"    (highlight true eb/DEFAULT_OPTIONS)
                       "mouse:out"     (highlight false eb/DEFAULT_OPTIONS)}
          :rels {:start "connector" :penultimate true}}

         {:name "arrow"
          :type :decorator
          :src  (arrow data options)}

         {:name "end"
          :type :endpoint
          :src  (endpoint conE :moveable true :display "circle" :visible true :opacity 0)
          :behaviours {"object:moving" (all (moving-endpoint)
                                            (intersects? "body" (fn [src trg] (toggle-endpoints (:entity trg) true))
                                                                (fn [src trg] (toggle-endpoints (:entity trg) false))))
                       "mouse:up"      (all (intersects-any? #{"connector-top" "connector-bottom" "connector-left" "connector-right"} (fn [src trg] (e/connect-entities (:entity src) (:entity trg) (:drawable src))
                                                                                                                                                    (toggle-endpoints (:entity trg) false)
                                                                                                                                                    (position-endpoint (:entity src) "end" (.-left (:src trg)) (.-top (:src trg)))))
                                            (event-wrap relations-validate))
                       "mouse:over"    (highlight true eb/DEFAULT_OPTIONS)
                       "mouse:out"     (highlight false eb/DEFAULT_OPTIONS)}
          :rels {:end "connector"}}])))
