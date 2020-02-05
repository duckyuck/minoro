# minoro

Minoro is a Clojure library for shrinking your data.

Given some data and a predicate you supply, `minoro.core/shrink-with` will returned a shrunk
variant of the provided data.

Your predicate will be fed various permutations of the data you provide. The job of
your predicate is to decide if you're happy with a given permutation by returning
either `true` or `false`.

Let's say we'd like to shrink this map:

```clj
(def data
  {:a "#!%&%#$&# minoro #$%&/(%%!"
   :b "&/$#&%#%& rules! )+(/$!$&%"
   :c (range -10 10)
   :d #{:superfluous :values})
```

We're happy as long as `:a` contains `"minoro"`, `:b` contains `"rules!"` and that the sum
of positive numbers in `:c` is greater than, let's say `15`.

Let's encode these predicates in a function called `ok?`:

```clj
(defn ok? [x]
  (and (->> x :a (re-find #"minoro"))
       (->> x :b (re-find #"rules!"))
       (->> x :c (filter pos?) (apply +) (< 15))))
```

Now, let's shrink `data` according to our predicates.

```clj
(require '[minoro.core :refer [shrink-with install!]])

(install!) ;; minoro doesn't shrink strings, let's install handlers for all supported data types

(shrink-with ok? data)

;; => {:a "minoro", :b "rules", :c (8 9)}
```

Great! That's pretty much the minimal dataset our predicate function is happy with.

Minoro ships with default implementations (a.k.a. factories) for shrinking Clojure vectors, maps, sets, strings and seqs.
However, the implementation for shrinking strings is not enabled by default, and must be either `install!`ed explicitly, or activated via
the `with-factories` helper macro. Here's an example of the latter:

```clj
(require '[minoro.core :refer [shrink-with with-factories all-factories]])

(def ok? (partial re-find #"shrink me"))

(with-factories all-factories
  (shrink-with ok? "!%3&%#$&# shrink me #$%&/(%%!"))

=> "shrink me"
```

## Extensibility

You can extend Minoro to work with any other data type. This is done by implementing protocols
in the [minoro.protocols](src/minoro/protocols.clj) protocols namespace. Have a look at the [minoro.impl](src/minoro/impl.clj) namespace for examples
on how Minoro implements these protocols for the basic Clojure data types.

## Install

* Leiningen: `[minoro "2020-02-05"]`
* deps.edn: `minoro {:mvn/version "2020-02-05"}`

## Development

Run tests with `clj -Atest`

## License

Copyright © 2020 Anders Furseth

[BSD-3-Clause](http://opensource.org/licenses/BSD-3-Clause), see LICENSE
