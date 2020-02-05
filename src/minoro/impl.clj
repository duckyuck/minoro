(ns minoro.impl
  (:require [clojure.set :as set]
            [minoro.protocols :as p]))

(defrecord InitialValueWrapper [create-wrapped-shrinkable wrapped-value initial-value initial-value? exhausted?]
  p/IShrinkable

  (shrink [this]
    (if initial-value?
      (assoc this :exhausted? true)
      (assoc this :initial-value? true)))

  (shrinkable? [this]
    (not initial-value?))

  (skip [this]
    (let [wrapped-shrinkable (create-wrapped-shrinkable wrapped-value)]
      (if initial-value?
        (p/shrink wrapped-shrinkable)
        wrapped-shrinkable)))

  (value [this] (if initial-value? initial-value wrapped-value))

  (exhausted? [this] (true? exhausted?)))

(defn create-initial-value-wrapper [initial-value create-wrapped-shrinkable wrapped-value]
  (map->InitialValueWrapper {:create-wrapped-shrinkable create-wrapped-shrinkable :wrapped-value wrapped-value :initial-value initial-value}))

(def create-nil-wrapper (partial create-initial-value-wrapper nil))

(def create-none-wrapper (partial create-initial-value-wrapper p/none))

(defn ^:dynamic create-shrinkable [data & [skip-none-wrapper?]]
  (if skip-none-wrapper?
    (p/create data)
    (create-none-wrapper p/create data)))

(defrecord Unshrinkable [val]
  p/IShrinkable
  (shrink [this] this)
  (shrinkable? [this] false)
  (skip [this] this)
  (value [this] val)
  (exhausted? [this] true))

(defn create-unshrinkable [x] (->Unshrinkable x))

(defrecord BinaryWrapper [left right value-kw combine]
  p/IShrinkable

  (shrink [this]
    (let [new-kw (if (and (= value-kw :left)
                          (not (p/shrinkable? left)))
                   :right
                   value-kw)
          new-value (p/shrink (new-kw this))]
      (assoc this
             new-kw new-value
             :value-kw new-kw)))

  (shrinkable? [this]
    (or (p/shrinkable? left) (p/shrinkable? right)))

  (skip [this]
    (let [new-value (p/skip (value-kw this))
          new-kw (if (and (= value-kw :left)
                          (p/exhausted? new-value))
                   :right
                   value-kw)]
      (cond-> (assoc this
                     value-kw new-value
                     :value-kw new-kw)
        (not= new-kw value-kw)
        (update new-kw p/shrink))))

  (value [this] (->> [(p/value left) (p/value right)]
                     (remove #{p/none})
                     combine))

  (exhausted? [this] (and (p/exhausted? right)
                          (or (p/exhausted? left) (= value-kw :right)))))

(def unshrinkable-none (create-unshrinkable p/none))

(defn create-binary-wrapper [left right combine]
  (map->BinaryWrapper {:left (or left unshrinkable-none)
                       :right (or right unshrinkable-none)
                       :value-kw :left
                       :combine combine}))

(defrecord CollectionValueWrapper [shrinkable-value wrap-value]
  p/IShrinkable
  (shrink [this] (update this :shrinkable-value p/shrink))
  (shrinkable? [this] (p/shrinkable? shrinkable-value))
  (skip [this] (update this :shrinkable-value p/skip))
  (value [this] (wrap-value (p/value shrinkable-value)))
  (exhausted? [this] (p/exhausted? shrinkable-value)))

(defn create-collection-value-wrapper [wrap-value value]
  (->CollectionValueWrapper (create-shrinkable (first value) true) wrap-value))

(defn create-collection [{:keys [seq->type combine create-shrinkable-value]} val]
  (let [[left right] (map seq->type (split-at (long (/ (count val) 2)) val))]
    (create-binary-wrapper
     (when (seq left)
       (if (= (count left) 1)
         (create-none-wrapper create-shrinkable-value left)
         (create-shrinkable left)))
     (when (seq right)
       (if (= (count right) 1)
         (create-none-wrapper create-shrinkable-value right)
         (create-shrinkable right)))
     combine)))

(let [args {:seq->type vec
            :combine (comp vec (partial apply concat))
            :create-shrinkable-value (partial create-collection-value-wrapper vector)}]
  (defn create-vector [val] (create-collection args val)))

(let [args {:seq->type sequence
            :combine (partial apply concat)
            :create-shrinkable-value (partial create-collection-value-wrapper (comp seq vector))}]
  (defn create-seq [val] (create-collection args val)))

(let [args {:seq->type set
            :combine (partial apply set/union)
            :create-shrinkable-value (partial create-collection-value-wrapper hash-set)}]
  (defn create-set [val] (create-collection args val)))

(let [args {:seq->type (partial apply str)
            :combine (partial apply str)
            :create-shrinkable-value (partial create-collection-value-wrapper str)}]
  (defn create-string [val] (create-collection args val)))

(defrecord MapEntry [k v]
  p/IShrinkable
  (shrink [this] (update this :v p/shrink))
  (shrinkable? [this] (p/shrinkable? v))
  (skip [this] (update this :v p/skip))
  (value [this] (let [v-val (p/value v)]
                  {k (if (= v-val p/none) nil v-val)}))
  (exhausted? [this] (p/exhausted? v)))

(defn create-map-entry [m]
  (let [[k v] (first m)]
    (->MapEntry k (create-shrinkable v))))

(let [args {:seq->type (partial into {})
            :combine (partial apply merge {})
            :create-shrinkable-value create-map-entry}]
  (defn create-map [val] (create-collection args val)))
