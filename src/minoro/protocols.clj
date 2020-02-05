(ns minoro.protocols)

(defprotocol IShrinkable
  (shrink [this])
  (skip [this])
  (shrinkable? [this])
  (value [this])
  (exhausted? [this]))

(def none :minoro/none)

(defprotocol IShrinkableFactory
  (create [this]))
