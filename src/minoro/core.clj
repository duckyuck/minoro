(ns minoro.core
  (:require [minoro.protocols :as p]
            [minoro.impl :as impl]
            [clojure.string :as str]
            [clojure.set :as set]))

(extend-type java.lang.Object
  p/IShrinkableFactory
  (create [this] (impl/create-unshrinkable this)))

(def all-factories
  [{:kind :vector :type clojure.lang.IPersistentVector :create impl/create-vector}
   {:kind :map :type clojure.lang.IPersistentMap :create impl/create-map}
   {:kind :set :type clojure.lang.IPersistentSet :create impl/create-set}
   {:kind :string :type java.lang.String :create impl/create-string}
   {:kind :seq :type clojure.lang.ISeq :create impl/create-seq}])

(def all-kinds (set (map :kind all-factories)))

(defn select-factories [kinds]
  (filter #(some #{(:kind %)} kinds) all-factories))

(defn install!
  ([] (install! all-factories))
  ([factories]
   (doseq [{:keys [type create]} factories]
     (extend type p/IShrinkableFactory {:create create}))))

(install! (select-factories (remove #{:string} all-kinds)))

(defn- create-shrinkable [factories data & [skip-none-wrapper?]]
  (let [create (or (:create (first (filter #(instance? (:type %) data) factories)))
                   p/create)]
    (if skip-none-wrapper?
      (create data)
      (impl/create-none-wrapper create data))))

(defmacro with-factories [factories & body]
  `(binding [impl/create-shrinkable (partial create-shrinkable ~factories)]
     ~@body))

(defn shrinkables-with [pred data]
  (let [shrinkable (impl/create-nil-wrapper #(impl/create-shrinkable % true) data)]
    (if-not (pred data)
      (throw (ex-info "supplied predication function returned false when supplied non-shrunk data" {}))
      (->> (reductions (fn [[shrinkable action] pred]
                         (let [shrinkable (action shrinkable)]
                           (let [shrunk-value (try
                                                (let [shrunk-value (p/value shrinkable)]
                                                  (when-not (= :minoro/none shrunk-value)
                                                    shrunk-value))
                                                (catch Exception e
                                                  (throw (ex-info "value threw exception" {:shrinkable shrinkable} e))))
                                 ok? (try
                                       (pred shrunk-value)
                                       (catch Exception e false))
                                 shrinkable (assoc shrinkable :minoro/ok? ok?)]
                             (cond
                               (and (not ok?) (p/exhausted? shrinkable))
                               (reduced [shrinkable])

                               (and ok? (not (p/shrinkable? shrinkable)))
                               (reduced [shrinkable])

                               :else
                               (if ok?
                                 [shrinkable p/shrink]
                                 [shrinkable p/skip])))))
                       [(assoc shrinkable :minoro/ok? true) p/shrink]
                       (repeat pred))
           (map first)))))

(def value p/value)

(defn shrink-with [pred data]
  (-> (shrinkables-with pred data)
      last
      value))
