(ns minoro.impl-test
  (:require [minoro.core]
            [minoro.impl :as sut]
            [minoro.protocols :as p]
            [clojure.test :refer [deftest is testing]]))

(deftest unshrinkable

  (let [x (sut/create-unshrinkable 42)]
    (is (= (p/shrink x) x))
    (is (not (p/shrinkable? x)))
    (is (= (p/skip x) x))
    (is (= (p/value x) 42))
    (is (p/exhausted? x) 42)))

(defn create-shrinkable [x]
  (sut/create-none-wrapper sut/create-unshrinkable x))

(deftest shrinkable-binary-node

  (let [x (sut/create-binary-wrapper (create-shrinkable 1)
                                     (create-shrinkable 2)
                                     identity)]

    (testing "starts off as combined value of right and left"
      (is (= (p/value x) [1 2]))
      (is (not (p/exhausted? x)))
      (is (p/shrinkable? x)))

    (testing "shrink"
      (let [x (p/shrink x)]
        (is (= (p/value x) [2]))
        (is (not (p/exhausted? x)))
        (is (p/shrinkable? x))

        (testing "skip"
          (let [x (p/skip x)]
            (is (= (p/value x) [1]))
            (is (not (p/exhausted? x)))
            (is (not (p/shrinkable? x)))

            (testing "skip"
              (let [x (p/skip x)]
                (is (= (p/value x) [1 2]))
                (is (p/exhausted? x))
                (is (not (p/shrinkable? x)))))))

        (testing "shrink"
          (let [x (p/shrink x)]
            (is (= (p/value x) []))
            (is (not (p/exhausted? x)))
            (is (not (p/shrinkable? x)))

            (testing "skip"
              (let [x (p/skip x)]
                (is (= (p/value x) [2]))
                (is (p/exhausted? x))
                (is (not (p/shrinkable? x))))))))

      (testing "skip"
        (let [x (p/skip x)]
          (is (= (p/value x) [1]))
          (is (not (p/exhausted? x)))
          (is (not (p/shrinkable? x)))

          (testing "skip"
            (let [x (p/skip x)]
              (is (= (p/value x) [1 2]))
              (is (p/exhausted? x))
              (is (not (p/shrinkable? x))))))))))

(deftest none-wrapper

  (let [create-wrapped sut/create-unshrinkable
        x (sut/create-none-wrapper sut/create-unshrinkable 42)]

    (testing "starts off as value"
      (is (= (p/value x) 42))
      (is (p/shrinkable? x))
      (is (not (p/exhausted? x))))

    (testing "shrink to :minoro/none"
      (let [x (p/shrink x)]
        (is (= (p/value x) :minoro/none))
        (is (not (p/shrinkable? x)))
        (is (not (p/exhausted? x)))

        (testing "skip returns wrapped shrinkable"
          (is (= (p/skip x) (create-wrapped 42))))))

    (testing "skip returns shrunk wrapped shrinkable"
      (is (= (p/skip x) (p/shrink (create-wrapped 42)))))))

(deftest collection
  (testing "0 elements"
    (let [x (sut/create-vector [])]
      (is (= (p/value x) []))
      (is (not (p/shrinkable? x)))
      (is (p/exhausted? x))))

  (testing "1 element"
    (let [x (sut/create-vector [[1]])]
      (is (= (p/value x) [[1]]))
      (is (p/shrinkable? x))
      (is (not (p/exhausted? x)))

      (let [x (p/shrink x)]
        (is (= (p/value x) []))
        (is (not (p/shrinkable? x)))
        (is (not (p/exhausted? x)))

        (let [x (p/skip x)]
          (is (= (p/value x) [[]]))
          (is (not (p/shrinkable? x)))
          (is (not (p/exhausted? x)))

          (let [x (p/skip x)]
            (is (= (p/value x) [[1]]))
            (is (not (p/shrinkable? x)))
            (is (p/exhausted? x)))))))

  (testing "2 elements"
    (let [x (sut/create-vector [1 [2]])]
      (is (= (p/value x) [1 [2]]))
      (is (p/shrinkable? x))
      (is (not (p/exhausted? x)))

      (let [x (p/shrink x)]
        (is (= (p/value x) [[2]]))
        (is (p/shrinkable? x))
        (is (not (p/exhausted? x)))

        (let [x (p/skip x)]
          (is (= (p/value x) [1]))
          (is (not (p/shrinkable? x)))
          (is (not (p/exhausted? x)))

          (let [x (p/skip x)]
            (is (= (p/value x) [1 []]))
            (is (not (p/shrinkable? x)))
            (is (not (p/exhausted? x)))

            (let [x (p/skip x)]
              (is (= (p/value x) [1 [2]]))
              (is (not (p/shrinkable? x)))
              (is (p/exhausted? x))))))))

  (testing "3 elements"
    (let [x (sut/create-vector [1 [2 3]])]
      (is (= (p/value x) [1 [2 3]]))
      (is (p/shrinkable? x))
      (is (not (p/exhausted? x)))

      (testing "shrink"
        (let [x (p/shrink x)]
          (is (= (p/value x) [[2 3]]))
          (is (p/shrinkable? x))
          (is (not (p/exhausted? x)))

          (testing "shrink"
            (let [x (p/shrink x)]
              (is (= (p/value x) []))
              (is (not (p/shrinkable? x)))
              (is (not (p/exhausted? x)))

              (testing "skip"
                (let [x (p/skip x)]
                  (is (= (p/value x) [[3]]))
                  (is (p/shrinkable? x))
                  (is (not (p/exhausted? x)))

                  (testing "shrink"
                    (let [x (p/shrink x)]
                      (is (= (p/value x) [[]]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [[3]]))
                          (is (not (p/shrinkable? x)))
                          (is (p/exhausted? x))))))

                  (testing "skip"
                    (let [x (p/skip x)]
                      (is (= (p/value x) [[2]]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [[2 3]]))
                          (is (not (p/shrinkable? x)))
                          (is (p/exhausted? x))))))))))

          (testing "skip"
            (let [x (p/skip x)]
              (is (= (p/value x) [1]))
              (is (not (p/shrinkable? x)))
              (is (not (p/exhausted? x)))

              (testing "skip"
                (let [x (p/skip x)]
                  (is (= (p/value x) [1 [3]]))
                  (is (p/shrinkable? x))
                  (is (not (p/exhausted? x)))

                  (testing "shrink"
                    (let [x (p/shrink x)]
                      (is (= (p/value x) [1 []]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [1 [3]]))
                          (is (not (p/shrinkable? x)))
                          (is (p/exhausted? x))))))

                  (testing "skip"
                    (let [x (p/skip x)]
                      (is (= (p/value x) [1 [2]]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [1 [2 3]]))
                          (is (not (p/shrinkable? x)))
                          (is (p/exhausted? x))))))))))))))

  (testing "4 elements"
    (let [x (sut/create-vector [1 2 3 4])]
      (is (= (p/value x) [1 2 3 4]))
      (is (p/shrinkable? x))
      (is (not (p/exhausted? x)))

      (testing "shrink"
        (let [x (p/shrink x)]
          (is (= (p/value x) [3 4]))
          (is (p/shrinkable? x))
          (is (not (p/exhausted? x)))

          (testing "shrink"
            (let [x (p/shrink x)]
              (is (= (p/value x) []))
              (is (not (p/shrinkable? x)))
              (is (not (p/exhausted? x)))

              (testing "skip"
                (let [x (p/skip x)]
                  (is (= (p/value x) [4]))
                  (is (p/shrinkable? x))
                  (is (not (p/exhausted? x)))

                  (testing "shrink"
                    (let [x (p/shrink x)]
                      (is (= (p/value x) []))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [4]))
                          (is (not (p/shrinkable? x)))
                          (is (p/exhausted? x))))))

                  (testing "skip"
                    (let [x (p/skip x)]
                      (is (= (p/value x) [3]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [3 4]))
                          (is (not (p/shrinkable? x)))
                          (is (p/exhausted? x))))))))))

          (testing "skip"
            (let [x (p/skip x)]
              (is (= (p/value x) [2 3 4]))
              (is (p/shrinkable? x))
              (is (not (p/exhausted? x)))

              (testing "shrink"
                (let [x (p/shrink x)]
                  (is (= (p/value x) [3 4]))
                  (is (p/shrinkable? x))
                  (is (not (p/exhausted? x)))

                  (testing "shrink"
                    (let [x (p/shrink x)]
                      (is (= (p/value x) []))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [4]))
                          (is (p/shrinkable? x))
                          (is (not (p/exhausted? x)))

                          (testing "shrink"
                            (let [x (p/shrink x)]
                              (is (= (p/value x) []))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))))

                          (testing "skip"
                            (let [x (p/skip x)]
                              (is (= (p/value x) [3]))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))

                              (testing "skip"
                                (let [x (p/skip x)]
                                  (is (= (p/value x) [3 4]))
                                  (is (not (p/shrinkable? x)))
                                  (is (p/exhausted? x))))))))))

                  (testing "skip"
                    (let [x (p/skip x)]
                      (is (= (p/value x) [2]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [2 4]))
                          (is (p/shrinkable? x))
                          (is (not (p/exhausted? x)))

                          (testing "shrink"
                            (let [x (p/shrink x)]
                              (is (= (p/value x) [2]))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))

                              (testing "skip"
                                (let [x (p/skip x)]
                                  (is (= (p/value x) [2 4]))
                                  (is (not (p/shrinkable? x)))
                                  (is (p/exhausted? x))))))

                          (testing "skip"
                            (let [x (p/skip x)]
                              (is (= (p/value x) [2 3]))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))

                              (testing "skip"
                                (let [x (p/skip x)]
                                  (is (= (p/value x) [2 3 4]))
                                  (is (not (p/shrinkable? x)))
                                  (is (p/exhausted? x))))))))))))

              (testing "skip"
                (let [x (p/skip x)]
                  (is (= (p/value x) [1 3 4]))
                  (is (p/shrinkable? x))
                  (is (not (p/exhausted? x)))

                  (testing "shrink"
                    (let [x (p/shrink x)]
                      (is (= (p/value x) [1]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [1 4]))
                          (is (p/shrinkable? x))
                          (is (not (p/exhausted? x)))

                          (testing "shrink"
                            (let [x (p/shrink x)]
                              (is (= (p/value x) [1]))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))

                              (testing "skip"
                                (let [x (p/skip x)]
                                  (is (= (p/value x) [1 4]))
                                  (is (not (p/shrinkable? x)))
                                  (is (p/exhausted? x))))))

                          (testing "skip"
                            (let [x (p/skip x)]
                              (is (= (p/value x) [1 3]))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))

                              (testing "skip"
                                (let [x (p/skip x)]
                                  (is (= (p/value x) [1 3 4]))
                                  (is (not (p/shrinkable? x)))
                                  (is (p/exhausted? x))))))))))

                  (testing "skip"
                    (let [x (p/skip x)]
                      (is (= (p/value x) [1 2]))
                      (is (not (p/shrinkable? x)))
                      (is (not (p/exhausted? x)))

                      (testing "skip"
                        (let [x (p/skip x)]
                          (is (= (p/value x) [1 2 4]))
                          (is (p/shrinkable? x))
                          (is (not (p/exhausted? x)))

                          (testing "shrink"
                            (let [x (p/shrink x)]
                              (is (= (p/value x) [1 2]))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))

                              (testing "skip"
                                (let [x (p/skip x)]
                                  (is (= (p/value x) [1 2 4]))
                                  (is (not (p/shrinkable? x)))
                                  (is (p/exhausted? x))))))

                          (testing "skip"
                            (let [x (p/skip x)]
                              (is (= (p/value x) [1 2 3]))
                              (is (not (p/shrinkable? x)))
                              (is (not (p/exhausted? x)))

                              (testing "skip"
                                (let [x (p/skip x)]
                                  (is (= (p/value x) [1 2 3 4]))
                                  (is (not (p/shrinkable? x)))
                                  (is (p/exhausted? x))))))))))))))))))

  (testing "preserves meta"
    (let [[one two :as value] (p/value (sut/create-vector ^:meta [^:meta-1 [1] ^:meta-2 [2]]))]
      (is (= (meta value) {:meta true}))
      (is (= (meta one) {:meta-1 true}))
      (is (= (meta two) {:meta-2 true})))))
