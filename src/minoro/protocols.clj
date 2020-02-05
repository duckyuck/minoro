(ns minoro.protocols)

(defprotocol IShrinkable
  (shrink [this])
  (skip [this])
  (shrinkable? [this])
  (value [this])
  (exhausted? [this]))

(defprotocol IShrinkableFactory
  (create [this]))
