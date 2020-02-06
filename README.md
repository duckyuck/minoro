# minoro

Minoro is a Clojure library for shrinking your data.

## Install

* deps.edn: `minoro {:mvn/version "2020-02-06"}`
* Leiningen: `[minoro "2020-02-06"]`

## Use

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

(install!) ;; minoro doesn't shrink strings by default, let's install handlers for all supported data types

(shrink-with ok? data)

;; => {:a "minoro", :b "rules", :c (8 9)}
```

Great! That's pretty much the minimal dataset our predicate function is happy with.

Minoro ships with default implementations (a.k.a. factories) for shrinking Clojure vectors, maps, sets, strings and seqs.
However, the implementation for shrinking strings is not enabled by default, and must be either `install!`ed explicitly or activated via
the `with-factories` helper macro. Here's an example of the latter:

```clj
(require '[minoro.core :refer [shrink-with with-factories all-factories]])

(def ok? (partial re-find #"shrink me"))

(with-factories all-factories
  (shrink-with ok? "!%3&%#$&# shrink me #$%&/(%%!"))

;; => "shrink me"
```

In the previous example, Minoro generated 26 permutations before exhaustedly finding the smallest one. Your dataset
is probably considerably larger, your predicate function possibly slower. If you're worried to be waiting
years before `shrink-with` is finally providing a shrunk variation of your data, `shrinkables-with` provides you the means
to control how many permutations to try.

`shrinkables-with` expects the same parameters as `shrink-with`. However, the result is a lazy sequence of all permutations
sent to your predicate function. The elements in this lazy sequence are object implementing the `minoro.protocols.IShrinkable`
protocol, wrapping the permutated value. The `value` function pulls the actual permutated value out of these object. The objects
also contains a key named `:minoro/ok?`, designating to hold the boolean result of your predicate function for that permutation.

A few lines of code tells more than a thousand words. Let's dump the value of all permutation marked as successful via `:minoro/ok?`.

```clj
  (require '[minoro.core :refer [shrinkables-with value]])

  (defn ok? [numbers]
    (clojure.set/subset? #{1 5 15} (set numbers)))

  (->> (range 0 20)
       (shrinkables-with ok?)
       (filter :minoro/ok?)
       (map value))

  ;; => ((0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19)
  ;;     (1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19)
  ;;     (1 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19)
  ;;     (1 5 7 8 9 10 11 12 13 14 15 16 17 18 19)
  ;;     (1 5 10 11 12 13 14 15 16 17 18 19)
  ;;     (1 5 15 16 17 18 19)
  ;;     (1 5 15 17 18 19)
  ;;     (1 5 15))
```

As the result of `shrinkable-with` is lazy, you're free to pull permutations from this sequence until you're fed up waiting for
Minoro to come up with an even more minimalistic version of your data.

A few words of caveat; be careful of using `shrinkable-with` in conjuction with the `with-factories` macro. `with-factories` sets
up thread local bindings via Clojure's `binding` macro. Exceptions mights be thrown if you were to realize the sequence returned
from `shrinkables-with` outside the scope of `with-factories`. Either realize the result inside the body of `with-factories` or
install the factories via `install!`.

## Extensibility

You can extend Minoro to work with any data type. This is accomplished by implementing protocols
in the [minoro.protocols](src/minoro/protocols.clj) namespace. Have a look at the [minoro.impl](src/minoro/impl.clj) namespace for examples
on how Minoro implements these protocols for the basic Clojure data types.

## Development

Run tests with `clj -Atest`

## License

Copyright © 2020 Anders Furseth

[BSD-3-Clause](http://opensource.org/licenses/BSD-3-Clause), see LICENSE
