(ns onyx.peer.custom-compression-test
  (:require [clojure.core.async :refer [chan >!! <!! close! sliding-buffer]]
            [clojure.test :refer [deftest is testing]]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.test-helper :refer [load-config with-test-env]]
            [onyx.api]))

(def n-messages 100)

(defn my-inc [{:keys [n] :as segment}]
  (assoc segment :n (inc n)))

(def in-chan (atom nil))

(def out-chan (atom nil))

(defn inject-in-ch [event lifecycle]
  {:core.async/chan @in-chan})

(defn inject-out-ch [event lifecycle]
  {:core.async/chan @out-chan})

(def in-calls
  {:lifecycle/before-task-start inject-in-ch})

(def out-calls
  {:lifecycle/before-task-start inject-out-ch})

(deftest compression
  (let [id (java.util.UUID/randomUUID)
        config (load-config)
        env-config (assoc (:env-config config) :onyx/id id)
        peer-config (assoc (:peer-config config)
                           :onyx/id id
                           :onyx.messaging/decompress-fn #(read-string (String. % "UTF-8"))
                           :onyx.messaging/compress-fn #(.getBytes (pr-str %)))
        batch-size 20

        catalog [{:onyx/name :in
                  :onyx/plugin :onyx.plugin.core-async/input
                  :onyx/type :input
                  :onyx/medium :core.async
                  :onyx/batch-size batch-size
                  :onyx/max-peers 1
                  :onyx/doc "Reads segments from a core.async channel"}

                 {:onyx/name :inc
                  :onyx/fn :onyx.peer.custom-compression-test/my-inc
                  :onyx/type :function
                  :onyx/batch-size batch-size}

                 {:onyx/name :out
                  :onyx/plugin :onyx.plugin.core-async/output
                  :onyx/type :output
                  :onyx/medium :core.async
                  :onyx/batch-size batch-size
                  :onyx/max-peers 1
                  :onyx/doc "Writes segments to a core.async channel"}]

        workflow [[:in :inc] [:inc :out]]

        lifecycles [{:lifecycle/task :in
                     :lifecycle/calls :onyx.peer.custom-compression-test/in-calls}
                    {:lifecycle/task :in
                     :lifecycle/calls :onyx.plugin.core-async/reader-calls}
                    {:lifecycle/task :out
                     :lifecycle/calls :onyx.peer.custom-compression-test/out-calls}
                    {:lifecycle/task :out
                     :lifecycle/calls :onyx.plugin.core-async/writer-calls}]]

    (reset! in-chan (chan (inc n-messages)))
    (reset! out-chan (chan (sliding-buffer (inc n-messages))))

    (with-test-env [test-env [3 env-config peer-config]]
      (doseq [n (range n-messages)]
        (>!! @in-chan {:n n}))

      (>!! @in-chan :done)
      (close! @in-chan)

      (onyx.api/submit-job peer-config
                           {:catalog catalog
                            :workflow workflow
                            :lifecycles lifecycles
                            :task-scheduler :onyx.task-scheduler/balanced})

      (let [results (take-segments! @out-chan)
            expected (set (map (fn [x] {:n (inc x)}) (range n-messages)))]
        (is (= expected (set (butlast results))))
        (is (= :done (last results)))))))
