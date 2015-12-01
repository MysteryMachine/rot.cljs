(ns play.core
  (:use arcadia.core)
  (:import
   [UnityEngine  Vector3
    NavMeshAgent Animator
    Debug        Physics
    Transform    RaycastHit
    Input        Ray
    Vector3      Camera
    Resources    Quaternion
    GameObject]
   Caster
   ArcadiaState))

;; Logging
(defn log [msg] (Debug/Log (str msg)))

(defn go? [obj] (= GameObject (type obj)))

;; Accessing Object
(defn the
  "For accessing an object based on a name and component"
  ([arg] (if (string? arg) (object-named arg) arg))
  ([obj component]
   (if-let [go (the obj)]
     (get-component go component)
     nil)))

(defn the*
  [obj component]
  (.GetComponentInChildren (the obj) component))

(defn ->go [obj] (.gameObject obj))

(defn transform [obj] (the obj Transform))

(defn parent [obj] (.parent (transform obj)))
(defn parent? [obj par] (= (parent obj) (transform par)))
(defn parent! [obj par]
  (set! (.parent (the obj Transform)) (the par Transform)))

(defn child-components
  ([name] (child-components name Transform))
  ([name component]
   (let [obj (the name)
         prelim (.GetComponentsInChildren obj component)]
     (filter #(parent? % obj) prelim))))

(defn children
  ([top-obj] (children top-obj identity))
  ([top-obj filter-fn]
   (let [kids (map ->go (child-components top-obj Transform))]
     (filter #(and (parent? % top-obj) (filter-fn %)) kids))))

(defn ->name ^String [obj] (.name obj))

;; Vector
(defn v3
  (^Vector3 [[x y z]] (Vector3. x y z))
  (^Vector3 [x y z]   (Vector3. x y z)))

(defn q4
  (^Quaternion [[x y z a]] (Quaternion. x y z a))
  (^Quaternion [x y z a] (Quaternion. x y z a)))

(defn mag    ^Double [obj] (.magnitude obj))
(defn sqmag  ^Double [obj] (.sqrMagnitude obj))
(defn normal ^Double [obj] (.normalized obj))

(defn position ^Vector3 [obj] (.position (transform obj)))
(defn dist [a b] (Vector3/Distance (position a) (position b)))

;; Nav Mesh Agent
(defn nav-mesh-agent ^NavMeshAgent [obj] (the obj NavMeshAgent))
(defn nav-mesh-agent* ^NavMeshAgent [obj] (the* obj NavMeshAgent))
(defn move!
  ([obj target]
   (let [coords (if (= Vector3 (type target))
                  target
                  (position target))]
     (set! (.destination (the obj NavMeshAgent)) coords)))
  ([obj x y z] (move! obj (v3 x y z))))

;; Animator
(defn animator  ^Animator [obj] (the obj Animator))
(defn animator* ^Animator [obj] (the* obj Animator))

;; Look into maybe using a macro to define all these
;; in the future?
(defmulti  anim-set*! #(type %3))
(defmethod anim-set*! Boolean [this ^String name arg]
  (.SetBool (animator* this) name arg))
(defmethod anim-set*! nil [this ^String name _]
  (.SetTrigger (animator* this) name))
(defmethod anim-set*! Double [this ^String name ^Double arg]
  (.SetFloat (animator* this) name (float arg)))
(defmethod anim-set*! Single [this ^String name arg]
  (.SetFloat (animator* this) name arg))
(defmethod anim-set*! Int64 [this ^String name ^Int64 arg]
  (.SetInteger (animator* this) name (int arg)))
(defmethod anim-set*! Int32 [this ^String name arg]
  (.SetInteger (animator* this) name arg))
(defmethod anim-set*! :default [this name arg]
  (throw (str "Unsure how to set animation " arg " for property " name)))

(defn sync-agent-velocity! [this]
  (anim-set*!
   this
   "velocity"
   (mag (.velocity (nav-mesh-agent* this)))))

;; Raycasting

(defn main-camera ^Camera [] (Camera/main))

(defn mouse-pos ^Vector3 [] (Input/mousePosition))

(defn right-click [] (Input/GetMouseButtonDown 1))
(defn right-held  [] (Input/GetMouseButton     1))
(defn right-up    [] (Input/GetMouseButtonUp   1))

(defn left-click  [] (Input/GetMouseButtonDown 0))
(defn left-held   [] (Input/GetMouseButton     0))
(defn left-up     [] (Input/GetMouseButtonUp   0))

(defn mouse->hit
  ([] (mouse->hit (fn [_] true) (fn [_] false)))
  ([obj-filter] (mouse->hit obj-filter (fn [_] false)))
  ([obj-filter point-filter]
   (let [ray (.ScreenPointToRay (main-camera) (mouse-pos))
         caster (Caster. ^Ray ray)]
     (if (.success caster)
       (let [info (.hit caster)
             go   (->go (.transform info))]
         (cond
           (obj-filter go) go
           (point-filter go) (.point info)
           :else nil))))))

;; Prefab
(defn clone!
  ([^GameObject obj]
   (let [go (GameObject/Instantiate obj)]
     (set! (.name go) (.name obj))
     go))
  ([^GameObject obj ^Vector3 pos ^Quaternion rot]
   (let [go (GameObject/Instantiate obj pos rot)]
     (set! (.name go) (.name obj))
     go)))

(defn prefab!
  ([^String name] (clone! (Resources/Load name)))
  ([^String name  ^Vector3 pos ^Quaternion rot]
   (clone! (Resources/Load name) pos rot)))

;; Arcadia State
(defn state-component [obj] (the obj ArcadiaState))
(defn state  [obj] (if-let [state-comp (state-component obj)]
                     (.state state-comp)
                     nil))
(defn state! [obj arg] (set! (.state (the obj ArcadiaState)) arg))
(defn swat! [obj fun]
  (let [st (the obj ArcadiaState)]
    (set! (.state st) (fun (.state st)))))

;; Id
(def ^:private id-gen (atom 0))
(def ^:private ids    (atom {}))
(def ^:private objs   (atom {}))

(defn ->id [obj]
  (let [go (->go obj)
        retval (get @objs go)]
    (if retval
      retval
      (let [retval (reset! id-gen (inc @id-gen))]
        (reset! ids  (assoc @ids  retval go))
        (reset! objs (assoc @objs go     retval))
        retval))))

(defn ->obj [id] (get @ids id))
