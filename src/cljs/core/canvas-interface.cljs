(ns core.canvas-interface
  (:require [utils.dom.dom-utils :as dom]
            [tailrecursion.javelin :refer [cell]]
            [tailrecursion.hoplon :refer [canvas by-id append-child add-children! ]]
            [core.settings :refer [settings
                                   settings?
                                   settings!
                                   page-formats
                                   page-width
                                   page-height
                                   ]])
  (:require-macros [tailrecursion.javelin :refer [cell=]]))

(declare add-item)
(declare visible-page)
(declare index-of-page)
(declare id2idx)
(declare idx2id)

(defn js-conj [jscontainer obj]
  (.add jscontainer obj)
  jscontainer
)

(defn js-rem [jscontainer obj]
  (.remove jscontainer obj)
  jscontainer
)

(def project (cell {:current-page-idx 0
                    :pages {}
                    :current-page-id "page-1"}))

(defn proj-create-page [id active]
  (let [page {:canvas (js/fabric.Canvas. id)
              :buffer {}
              :groups {}
              :index index
              :id id }]
    (swap! project assoc-in [:pages (keyword id)] page)
    (when (true? active) (do
                           (swap! project assoc-in [:current-page-id] id)
                           (swap! project assoc-in [:current-page-idx] (id2idx id))))))

(defn proj-select-page [id]
  (swap! project assoc-in [:current-page-id] id)
  (swap! project assoc-in [:current-page-idx] (id2idx id))
)

(defn proj-page-by-id [id]
  (dom/console-log (keyword id))
  (dom/console-log (get-in @project [:pages (keyword id)]))
  (get-in @project [:pages (keyword id)])
)

(defn proj-selected-page []
  (let [id (get-in @project [:current-page-id])]
    (dom/console-log id)
    (get-in @project [:pages (keyword id)])))

;; (defmacro within-page [domid body]
;;   `(let [page (get-in @project [:pages (keyword ~@domid)])]
;;      ~@body))

(defn group-elem-count [id key-group]
  (let [page (get-in @project [:pages (keyword id)])]
    (count (keys (get-in page [:groups key-group])))))

(defn add-item
  ([id item key]
     (.add (:canvas (proj-page-by-id id)) item)
     (swap! project assoc-in [:pages (keyword id) :buffer key] item))
  ([id item key group]
     (add-item id item key)
     (swap! project assoc-in [:pages (keyword id) :groups group (group-elem-count id group)] key)))

(defn del-item [id key]
  (let [page (get-in @project [:pages (keyword id)])]
    (let [elem (get-in page [:buffer key])]
      (when (not (nil? elem)) (.remove (:canvas page) elem)))))

(defn clear-group [id group-name]
  (let [key-map (get @node-keys-group key-group)]
    (doseq [key (keys key-map)]
      (del-item (get key-map key))))
   (swap! node-keys-group dissoc group))

(defn items-in-group [pageid group-key]
  (let [page (get-in @project [:pages (keyword pageid)])]
    (get (:groups page) group-key)))

(defn iterate-group [group-key])

(defn draw-grid [id]
 ;; (del-items "grid")
  (let [page (get-in @project [:pages (keyword id)])]
    (when (= true @(settings? :snapping :visible))
      (loop [x 0 y 0]
        (if (<= @page-width x)
          x
          (let [line1 (js/fabric.Rect. (js-obj "left" 0
                                               "top" y
                                               "width" @page-width
                                               "height" 1
                                               "opacity" 0.1)),
                line2 (js/fabric.Rect. (js-obj "left" x
                                               "top" 0
                                               "width" 1
                                               "height" @page-height
                                               "opacity" 0.1))]
            (doseq [line [line1 line2]]
              (set! (.-selectable line) false)
              (let [key (str "grid-" (group-elem-count (:id page) "grid"))]
                (add-item (:id page) line key "grid")))
            (recur (+ @(settings? :snapping :interval) x) (+ @(settings? :snapping :interval) y))))))))


;;Not yet used. NEED TESTING.
(defn reactive-grid [settings]
   (loop [indx 0 offset 0]
           (if (< indx (group-count "grid"))
             (let [key (get (group? "grid") indx),
                   key2 (get (group? "grid") (inc indx))]
               (let [elem (get @canvas-buffer key),
                     elem2 (get @canvas-buffer key2)]
                 (dom/console-log (str key " , " key2))
                 (.setTop elem offset)
               ;;(.set elem "width" (page-width))
                 (.setLeft elem2 offset)
               ;;(.set elem2 "height" (page-height))
                 (.setVisible elem (get-in settings [:snapping :visible]))
                 (.setVisible elem2 (get-in settings [:snapping :visible])))
               (recur (+ 2 indx) (+ (get-in settings [:snapping :interval]) offset)))
             indx ))
)

(defn snap! [target pos-prop pos-prop-set direction]
  (let  [div  (quot (pos-prop target) (:interval (:snapping @settings))),
         rest (mod  (pos-prop target) (:interval (:snapping @settings)))]
    (let [neww (* div (:interval (:snapping @settings)))]
      (when (< rest (:attract (:snapping @settings))) (pos-prop-set target neww)))))

(defn do-snap [event]
  (when (= true (:enabled (:snapping @settings)))
    (let [target (.-target event)]
      (snap! target #(.-left %) #(set! (.-left %) %2) 1)
      (snap! target #(.-top  %) #(set! (.-top %)  %2) 1)))
)

(defn page-id [indx]
  (str "page-" indx))

;;bad implemented. CORRECT THIS FURHTER
(defn idx2id [idx]
  (let [node (.get (dom/j-query-class "canvas-container") idx)]
     (if (not (nil? node))
       (.attr (.first (.children (dom/j-query node))) "id") -1)))

(defn id2idx [id]
  (let [c-container (dom/parent (by-id id))]
    (.index (dom/j-query-class "canvas-container") c-container)))

(defn initialize-page [domid]
  (dom/wait-on-element domid (fn [id]
                               (dom/console-log (str "Initializing canvas with id [ " id " ]."))
                               (proj-create-page id true)
                               (cell= (.setDimensions (:canvas (proj-page-by-id id))
                                                                (js-obj "width"  page-width
                                                                        "height" page-height)
                                                                (js-obj "cssOnly" true)))
                               (draw-grid id)
                               (.on (:canvas (proj-page-by-id id)) (js-obj "object:moving" #(do-snap %))))))

(defn dispose-page [domid]

)

(defn create-page [id]
  (when (nil? (by-id id))
     (let [new (canvas :id id
                       :class "canvas")]
       (let [wrapper (by-id "canvas-wrapper")]
         (append-child wrapper new)
         (initialize-page id)))))

(defn remove-page [id]
  (when (not (nil? (by-id id)))
    (dispose-page id)
    (dom/remove-element (dom/parent (by-id id)))))

(defn select-page [index]
  (dom/console-log "in select-page")
  (let [id (idx2id index)]
    (when (not (= (get-in project [:current-page-id]) id))
      (do (dom/console-log (str "selecting page :" index ", id :" id))
          (swap! project assoc-in [:current-page-id] id)
          (visible-page (idx2id index))))))

(defn visible-page [id]
  (.css (js/jQuery ".canvas-container") "display" "none")
  (.css (.parent (js/jQuery (str "#" id))) "display" "block"))

(defn- repeat-action-on-condition [{:keys [init-index condition action recur-func]}]
  (loop [indx init-index]
    (if (condition indx) (do (action indx) (recur (recur-func indx))) true)))

(defn manage-pages [settings]
  (dom/console-log "In manage-pages")
  (cond
    (true? (get-in settings [:multi-page]))
      (when (!= (dom/children-count (by-id "canvas-wrapper"))
                (get-in settings [:pages :count]))
        (do
          (repeat-action-on-condition
           {:init-index (dom/children-count (by-id "canvas-wrapper"))
            :condition #(< (get-in settings [:pages :count]) %)
            :action #(remove-page (page-id (dec %)))
            :recur-func #(dec %)})
          (repeat-action-on-condition
           {:init-index 0
            :condition #(< % (get-in settings [:pages :count]) )
            :action #(create-page (page-id %))
            :recur-func #(inc %)})))
      :else (do
              (repeat-action-on-condition
                              {:init-index 1
                               :condition #(< % (dom/children-count (by-id "canvas-wrapper")))
                               :action #(remove-page (page-id %))
                               :recur-func #(inc %)})
              (create-page (page-id 0)))))

(defn initialize-workspace []
  ;; WHY those formulas are evaluated all the time !?!
  (cell= (manage-pages settings))
  (cell= (select-page (get-in project [:current-page-idx])))
)

(defmulti add :type)

(defmethod add "dom" [data]
  (let [photo-node (js/fabric.Image.
                           (:data data)
                           (js-obj "left"(:left (:params data))
                                   "top" (:top  (:params data))
                                   "angle"   0
                                   "opacity" 1))]
    ;;(dom/console-log (get-in @project [:current-page-id]))
    (.add (:canvas (proj-selected-page)) photo-node)))

(defmethod add "raw" [data])

(defmethod add "url" [data])
