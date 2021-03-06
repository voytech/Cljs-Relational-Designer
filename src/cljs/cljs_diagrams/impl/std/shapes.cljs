(ns cljs-diagrams.impl.std.shapes
  (:require [cljs-diagrams.core.shapes :as d :refer [layout-attributes]]
            [cljs-diagrams.impl.layouts.expression :as w :refer [layout-hints]]
            [cljs-diagrams.core.funcreg :refer [serialize]]
            [cljs-diagrams.core.layouts :as l :refer [layout
                                                      weighted-position
                                                      weighted-size
                                                      weighted-origin
                                                      match-parent-size
                                                      match-parent-position
                                                      margins]])
  (:require-macros [cljs-diagrams.core.macros :refer [defshape defshapes-group with-layouts shape defp]]))

(defn- control-initializer [size]
  (fn [container props]
     {:width size
      :height size
      :opacity 1
      :background-color "white"
      :border-color "black"
      :visible true}))

(defn- relation-initializer []
 (fn [container props]
   {:x1  0
    :y1  0
    :x2 (-> container :size :width)
    :y2 0
    :left 0
    :top 0
    :border-color "black"
    :stroke-style :dashed
    :border-width 1
    :z-index 0}))

(defn- endpoint-initializer [type visible]
  (fn [container props]
     {:left (- (if (= :start type) 0 (-> container :size :width)) 5)
      :top (- 5)
      :width 10
      :z-index :top
      :height 10
      :background-color "white"
      :border-color "black"
      :visible visible}))

(defn- arrow-initializer []
  (fn [container props]
    {:left (-> container :size :width)
     :top  0
     :origin-x :center
     :origin-y :center
     :angle 0
     :width 20
     :z-index :before-top
     :border-color "black"
     :border-style :solid
     :border-width 3
     :background-color "none"
     :height 20}))

(defn- node-shape-initializer []
  (fn [container props]
    {:border-color "black"
     :border-style :solid
     :border-width 1
     :background-color "white"}))

(defn poly-line-initializer []
  (fn [container props]
    {:points  [0 0 (-> container :bbox :width) 0]
     :left 0
     :top 0
     :border-color "black"
     :background-color "none"
     :stroke-style :solid
     :border-width 1
     :z-index 0}))

(defshape relation {:rendering-method :draw-poly-line
                    :initializer (poly-line-initializer)})

(defp triangle-bbox-draw []
  (fn [component]
    (let [x (d/get-left component)
          y (d/get-top component)
          width (d/get-width component)
          height (d/get-height component)]
     {:angle (d/getp component :angle)
      :points [x y
               (+ x width) (+ y (/ height 2))
               x (+ y height)]})))

(defshape arrow {:rendering-method :draw-poly-line
                 :model-customizers [(d/bbox-draw triangle-bbox-draw)]
                 :initializer (arrow-initializer)})

(defshape startpoint {:rendering-method :draw-rect
                      :initializer (endpoint-initializer :start true)})

(defshape endpoint {:rendering-method :draw-rect
                    :initializer (endpoint-initializer :end false)})

(defshape breakpoint {:rendering-method :draw-circle
                      :initializer (fn [e] {:moveable true :visible true :opacity 1 :z-index :top})})

(defshape control {:rendering-method :draw-rect
                   :initializer (control-initializer 16)})

(defshape node-shape {:rendering-method :draw-rect
                      :initializer (node-shape-initializer)})

;; ===================================
;; layout managed components.
;; ===================================
(defn- title-initializer []
  (fn [container props]
    {:border-color "black"
     :border-style :solid
     :border-width 1
     :font-family "calibri"
     :font-size 12}))

(defshape title {:rendering-method :draw-text
                 :initializer (title-initializer)})

(defn- image-initializer
  ([width height]
   (fn [container props]
     {:border-color "black"
      :stroke-style :solid
      :border-width 1
      :width width
      :height height}))
  ([width height image]
   (fn [container props]
     {:border-color "black"
      :stroke-style :solid
      :border-width 1
      :image-url image
      :width width
      :height height})))

(defshape image {:rendering-method :draw-image
                 :initializer (image-initializer 50 50)})

(defshape text {:rendering-method :draw-text
                :initializer (fn [c p] {:border-color "black" :border-style :solid :border-width 1 :font-family "calibri" :font-size 12})})

(defshape textarea {:rendering-method :draw-textarea
                    :initializer (fn [c p] {:multiline-text true :border-color "black" :border-style :solid :border-width 1 :font-family "calibri" :font-size 12})})

(defshape description {:rendering-method :draw-text
                       :initializer (fn [c p] {:border-color "black" :border-style :solid :border-width 1 :font-family "calibri" :font-size 12})})

(defshape rectangle {:rendering-method :draw-rect
                     :initializer (fn [c p] {:border-color "black" :border-style :solid :border-width 1})})

(defshape bounding-box {:rendering-method :draw-rect
                        :initializer (fn [c p] {:border-color "gray" :stroke-style :dashed :opacity 0.1 :border-width 1 :visible false})})

(defshape remove {:rendering-method :draw-image
                  :initializer (image-initializer 20 20 "/icons/remove.svg")})

(defshape edit {:rendering-method :draw-image
                :initializer (image-initializer 20 20 "/icons/edit.svg")})

(defshapes-group node-editing
  (with-layouts (layout "edit-buttons" ::w/expression))
  (shape edit   {:name  "edit"
                 :model {:width 20 :height 20 :background-color "red"}
                 :layout-attributes (layout-attributes "edit-buttons"  (layout-hints (weighted-position 1 0) (weighted-origin 2 1.2)))})
  (shape remove {:name  "remove"
                 :model {:width 20 :height 20}
                 :layout-attributes (layout-attributes "edit-buttons"  (layout-hints (weighted-position 1 0) (weighted-origin 1 1.2)))}))

(defshapes-group node-controls
  (with-layouts (layout "controls" ::w/expression))
  (shape control {:name  "connector-left"
                  :attributes {:side :left}
                  :layout-attributes (layout-attributes "controls"  (layout-hints (weighted-position 0 0.5) (weighted-origin 0.5 0.5)))})
  (shape control {:name  "connector-right"
                  :attributes {:side :right}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 1 0.5) (weighted-origin 0.5 0.5)))})
  (shape control {:name  "connector-top"
                  :attributes {:side :top}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 0.5 0) (weighted-origin 0.5 0.5)))})
  (shape control {:name "connector-bottom"
                  :attributes {:side :bottom}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 0.5 1) (weighted-origin 0.5 0.5)))}))

(defshapes-group small-controls
  (with-layouts (layout "controls" ::w/expression))
  (shape control {:name  "connector-left"
                  :attributes {:side :left}
                  :model {:width 10 :height 10}
                  :layout-attributes (layout-attributes "controls"  (layout-hints (weighted-position 0 0.5) (weighted-origin 0.5 0.5)))})
  (shape control {:name  "connector-right"
                  :attributes {:side :right}
                  :model {:width 10 :height 10}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 1 0.5) (weighted-origin 0.5 0.5)))})
  (shape control {:name  "connector-top"
                  :attributes {:side :top}
                  :model {:width 10 :height 10}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 0.5 0) (weighted-origin 0.5 0.5)))})
  (shape control {:name "connector-bottom"
                  :attributes {:side :bottom}
                  :model {:width 10 :height 10}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 0.5 1) (weighted-origin 0.5 0.5)))}))

(defshapes-group smallest-controls
  (with-layouts (layout "controls" ::w/expression))
  (shape control {:name  "connector-left"
                  :attributes {:side :left}
                  :model {:width 8 :height 8}
                  :layout-attributes (layout-attributes "controls"  (layout-hints (weighted-position 0 0.5) (weighted-origin 0.5 0.5)))})
  (shape control {:name  "connector-right"
                  :attributes {:side :right}
                  :model {:width 8 :height 8}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 1 0.5) (weighted-origin 0.5 0.5)))})
  (shape control {:name  "connector-top"
                  :attributes {:side :top}
                  :model {:width 8 :height 8}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 0.5 0) (weighted-origin 0.5 0.5)))})
  (shape control {:name "connector-bottom"
                  :attributes {:side :bottom}
                  :model {:width 8 :height 8}
                  :layout-attributes (layout-attributes "controls" (layout-hints (weighted-position 0.5 1) (weighted-origin 0.5 0.5)))}))
