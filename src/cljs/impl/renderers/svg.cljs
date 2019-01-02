(ns impl.renderers.svg
  (:require [core.components :as d]
            [core.entities :as e]
            [core.eventbus :as b]
            [core.rendering :as r]
            [core.state :as state]
            [core.utils.dom :as dom]
            [core.utils.svg :as svg]
            [core.eventbus :as bus]
            [impl.components :as impld]))

(defn- simple-set [to]
  (fn [svg val mdl]
    (dom/attr svg (name to) val)))

(defonce svg-property-mapping {:left (simple-set :x)
                               :top  (simple-set :y)
                               :round-x (simple-set :rx)
                               :round-y (simple-set :ry)
                               :width  (simple-set :width)
                               :height (simple-set :height)
                               :angle  (fn [svg val mdl]
                                          (dom/attr svg "transform"
                                            (str "rotate(" val "," (:left mdl) "," (:top mdl) ")")))
                               :x1 (simple-set :x1)
                               :y1 (simple-set :y1)
                               :x2 (simple-set :x2)
                               :y2 (simple-set :y2)
                               :border-color  (simple-set :stroke)
                               :background-color (simple-set :fill)
                               :radius (simple-set :r)
                               :font-family (simple-set :font-family)
                               :font-weight (simple-set :font-weight)
                               :font-size (simple-set :font-size)
                               :opacity (simple-set :fill-opacity)
                               :text-align (simple-set :text-align)
                               :text (fn [svg val mdl] (dom/set-text svg val))
                               :visible (fn [svg val mdl] (dom/attr svg "visibility" (if (== val true) "visible" "hidden")))
                               :color (simple-set :stroke)
                               :border-style (simple-set :stroke-style)
                               :border-width (simple-set :stroke-width)
                               :image-url (simple-set :href)})

(defonce constants-bindings {:top 100000
                             :before-bottom 1
                             :bottom 0})

(defn- resolve-value [val]
 (if (keyword? val)
   (or (val constants-bindings) val)
   val))

(defn- model-attributes [component]
  (let [model (d/model component)]
    (apply merge (mapv (fn [e] {e (resolve-value (e model))}) (keys model)))))

(defn- sync-svg-element
  ([svg component-model]
   (sync-svg-element svg component-model (keys component-model)))
  ([svg component-model attributes]
   (doseq [attribute attributes]
     (let [func (attribute svg-property-mapping)]
       (when (not (nil? func))
         (func svg (resolve-value (attribute component-model)) component-model))))))

(defn- update-svg-element [renderer-state component postprocess]
  (let [rendering-component (get-in @renderer-state [:components (:uid component)])
        source  (dom/by-id (:uid component))
        model  (model-attributes component)
        properties (:redraw-properties rendering-component)]
    (sync-svg-element source model properties)
    (when (not (nil? postprocess)) (postprocess source))))

(defn- create-svg-element [renderer-state svg-name component postprocess]
  (let [root (dom/by-id (-> renderer-state deref :root))
        source (svg/create-element svg-name root {})
        model (model-attributes component)]
      (sync-svg-element source model)
      (dom/attr source "id" (:uid component))
      (when (not (nil? postprocess)) (postprocess source))))

(defn- z-index-sorted [svg-components]
  (sort-by #(-> % :attributes :z-index) (vals svg-components)))

(defn- measure-text [app-state component]
  (when-let [domnode (dom/by-id (:uid component))]
    (let [bbox (.getBBox domnode)]
      (d/set-data component {:width (.-width bbox) :height (.-height bbox)}))))
      ;(bus/fire app-state "layouts.do" {:container (e/lookup app-state component)}))))

(defmethod r/is-state-created :svg [renderer-state component]
   (some? (dom/by-id (:uid component))))
;;==========================================================================================================
;; rendering context initialization
;;==========================================================================================================
(defmethod r/initialize :svg [renderer app-state dom-id width height initial-state]
  (let [renderer-state (atom initial-state)]
    (swap! renderer-state assoc :measure (fn [component] (measure-text app-state component))
                                :root (str dom-id "-svg"))
    (svg/create-svg dom-id "svg" {:width width :height height})
    renderer-state))

(defmethod r/all-rendered :svg [state context])
;;==========================================================================================================
;; rect rendering
;;==========================================================================================================
(defmethod r/do-render [:svg :draw-rect] [renderer-state component]
  (update-svg-element renderer-state component nil))

(defmethod r/create-rendering-state [:svg :draw-rect] [renderer-state component]
  (create-svg-element renderer-state "rect" component nil))

(defmethod r/destroy-rendering-state [:svg :draw-rect] [renderer-state component]
  (swap! renderer-state update :components dissoc (:uid component))
  (dom/remove-by-id (:uid component)))

;;==========================================================================================================
;; circle rendering
;;==========================================================================================================

(defn- circle [component]
  (fn [svg]
    (let [mdl (model-attributes component)]
      (dom/attrs svg {"cx" (+ (:left mdl ) (:radius mdl))
                      "cy" (+ (:top  mdl ) (:radius mdl))}))))

(defmethod r/do-render [:svg :draw-circle] [renderer-state component]
  (update-svg-element renderer-state component (circle component)))


(defmethod r/create-rendering-state [:svg :draw-circle] [renderer-state component]
  (create-svg-element renderer-state "circle" component (circle component)))

(defmethod r/destroy-rendering-state [:svg :draw-circle] [renderer-state component]
  (swap! renderer-state update :components dissoc (:uid component))
  (dom/remove-by-id (:uid component)))

;;==========================================================================================================
;; line rendering
;;==========================================================================================================
(defmethod r/do-render [:svg :draw-line] [renderer-state component]
  (update-svg-element renderer-state component nil))

(defmethod r/create-rendering-state [:svg :draw-line] [renderer-state component]
  (create-svg-element renderer-state "line" component nil))

(defmethod r/destroy-rendering-state [:svg :draw-line] [renderer-state component]
  (swap! renderer-state update-in [:components] dissoc (:uid component))
  (dom/remove-by-id (:uid component)))

;;==========================================================================================================
;; triangle rendering
;;==========================================================================================================
(defn- triangle-from-pos [component]
  (let [model (model-attributes component)
        x (:left model)
        y (:top model)
        width (:width model)
        height (:height model)]
   (str "M " (- x (/ width 2)) "," (+ y (/ height 2)) " "
             (+ x (/ width 2)) "," (+ y (/ height 2)) " "
              x "," (- y (/ height 2))
        " z")))

(defn- triangle [component]
  (fn [svg]
    (dom/attr svg "d" (triangle-from-pos component))))

(defmethod r/do-render [:svg :draw-triangle] [renderer-state component]
  (update-svg-element renderer-state component (triangle component)))

(defmethod r/create-rendering-state [:svg :draw-triangle] [renderer-state component]
  (create-svg-element renderer-state "path" component (triangle component)))

(defmethod r/destroy-rendering-state [:svg :draw-triangle] [renderer-state component]
  (swap! renderer-state update :components dissoc (:uid component))
  (dom/remove-by-id (:uid component)))

;;==========================================================================================================
;; text rendering
;;==========================================================================================================
(defmethod r/do-render [:svg :draw-text] [renderer-state component]
  (update-svg-element renderer-state component nil)
  ((:measure @renderer-state) component))

(defmethod r/create-rendering-state [:svg :draw-text] [renderer-state component]
  (create-svg-element renderer-state "text" component nil))

(defmethod r/destroy-rendering-state [:svg :draw-text] [renderer-state component]
  (swap! renderer-state update :components dissoc (:uid component))
  (dom/remove-by-id (:uid component)))

;;==========================================================================================================
;; image rendering
;;==========================================================================================================
(defmethod r/do-render [:svg :draw-image] [renderer-state component]
  (update-svg-element renderer-state component nil))

(defmethod r/create-rendering-state [:svg :draw-image] [renderer-state component]
  (create-svg-element renderer-state "image" component nil))

(defmethod r/destroy-rendering-state [:svg :draw-image] [renderer-state component]
  (swap! renderer-state update :components dissoc (:uid component))
  (dom/remove-by-id (:uid component)))
