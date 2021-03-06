(ns onyx.state.state-extensions)

(defmulti initialize-log 
  (fn [log-type event]
    log-type))

(defmulti close-log 
  (fn [log event]
    (type log)))

(defmulti compact-log
  "Compact a log for quicker replay and to reduce storage demands
  Returns a future that will perform the compaction transparently."
  (fn [log event state]
    (type log))) 

(defmulti store-log-entry 
  "Store state update [segment-id [[extent [op k v]] ..]] entries in a log"
  (fn [log event ack-fn entry]
    (type log)))

(defmulti playback-log-entries 
  "Read state update entries from log"
  (fn [log event state apply-fn]
    (type log)))

(defmulti initialize-filter 
  "Initialises a filter"
  (fn [filter-type event]
    filter-type))

(defmulti apply-filter-id 
  "Read applies a filter id to a filter state"
  (fn [filter-state event id]
    (type filter-state)))

(defmulti filter?
  "Has an id been seen before?"
  (fn [filter-state event id]
    (type filter-state)))

(defmulti close-filter
  "Closes a filter"
  (fn [filter-state event]
    (type filter-state)))

(defmulti snapshot-filter 
  "Returns a future containing a snapshot of the filter"
  (fn [filter-state event]
    (type filter-state)))

(defmulti restore-filter 
  "Restores the state of the filter from a snapshot"
  (fn [filter-state event snapshot]
    (type filter-state)))
