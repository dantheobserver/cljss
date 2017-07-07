(ns cljss.core
  (:require [cljss.utils :refer [build-css]]))

(defn- varid [id idx [rule]]
  [rule (str "--css-" id "-" idx)])

(defn- dynamic? [[_ value]]
  (not (or (string? value)
           (number? value))))

(defn- pseudo? [[rule value]]
  (and (re-matches #"&:.*" (name rule))
       (map? value)))

(defn- collect-styles [cls id idx styles]
  (let [dynamic (filterv dynamic? styles)
        static (filterv (comp not dynamic?) styles)
        vars (map-indexed #(varid id (+ idx %1) %2) dynamic)
        vals (mapv (fn [[_ var] [_ exp]] [var exp]) vars dynamic)
        static (->> vars
                    (map (fn [[rule var]] [rule (str "var(" var ")")]))
                    (concat static)
                    (build-css cls))]
    [static vals (count vars)]))

(defn- build-styles [styles]
  (let [pseudo (filterv pseudo? styles)
        styles (filterv (comp not pseudo?) styles)
        id (-> styles hash str)
        cls (str "css-" id)
        [static vals idx] (collect-styles cls id 0 styles)
        pstyles (->> pseudo
                     (map (fn [[rule styles]]
                            (collect-styles (str cls (subs (name rule) 1)) id idx styles))))
        static (->> pstyles
                    (map first)
                    (apply str)
                    (str static))
        vals (->> pstyles
                  (mapcat second)
                  (into vals))]
    [id static vals]))

(defmacro defstyles
  "Takes var name, a vector of arguments and a hash map of styles definition.
   Generates class name, static and dynamic parts of styles.
   Returns a function that calls `cljss.core/css` to inject styles at runtime
   and return generated class name."
  [var args styles]
  (let [[id# static# vals#] (build-styles styles)]
    `(defn ~var ~args
       (cljss.core/css ~id# ~static# ~vals#))))

(defn- ->styled
  "Takes a hash map of styles definition.
   Generates and returns class name, static and dynamic parts of styles."
  [styles]
  (let [attrs (->> styles (map second) (filterv keyword?))
        [id static vals] (build-styles styles)]
    [id static vals attrs]))

(defn- vals->array [vals]
  (let [arrseq (mapv (fn [[var val]] `(cljs.core/array ~var ~val)) vals)]
    `(cljs.core/array ~@arrseq)))

(defmacro defstyled
  "Takes var name, HTML tag name and a hash map of styles definition.
   Returns a var bound to the result of calling `cljss.core/styled`,
   which produces React element and injects styles."
  [var tag styles]
  (let [tag# (name tag)
        [id# static# vals attrs] (->styled styles)
        vals# (vals->array vals)
        attrs# `(cljs.core/array ~@attrs)]
    `(def ~var
       (cljss.core/styled ~tag# ~id# ~static# ~vals# ~attrs#))))
