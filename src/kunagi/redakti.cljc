(ns kunagi.redakti
  (:require
   [clojure.spec.alpha :as s]
   [kunagi.redakti.buffer :as buffer]))


(s/def ::identifier #(= % ::identifier))
(s/def ::redakti (s/keys :req [::identifier]))


(defn redakti? [redakti]
  (s/valid? ::redakti redakti))


(defn node [redakti]
  (buffer/node-under-cursor (-> redakti :buffer)))


(defn node-payload [redakti]
  (-> redakti node :redakti.node/payload))


(defn reg-action [redakti action]
  (assoc-in redakti [:actions (-> action :ident)] action))


(defn map-key [redakti key action]
  (assoc-in redakti [:keymap key] action))


(defn !message [redakti message-type message]
  (assoc redakti :message [message-type message]))


(defn !goto-sub-buffer [redakti buffer]
  (let [buffer (assoc buffer :parent-puffer (-> redakti :buffer))]
    (-> redakti
        (dissoc :message)
        (assoc :buffer buffer))))

(defn !cursor
  ([redakti f-new-cursor]
   (!cursor redakti f-new-cursor nil))
  ([redakti f-new-cursor alt-action]
   (let [cursor (-> redakti :buffer :cursor)
         tree (-> redakti :buffer :tree)
         cursor (f-new-cursor cursor tree)]
     (tap> [:!!! ::!cursor cursor])
     (if-not cursor
       (or alt-action redakti)
       (if (= (-> redakti :buffer :cursor) cursor)
         redakti
         (assoc-in redakti [:buffer :cursor] cursor))))))


(defn- reg-action--cursor-step-in [redakti]
  (reg-action
   redakti
   {:ident :cursor-step-in
    :f     #(!cursor % buffer/path-first-child)}))

(defn- reg-action--cursor-step-out [redakti]
  (reg-action
   redakti
   {:ident :cursor-step-out
    :f     #(!cursor % buffer/path-parent :goto-parent-buffer)}))

(defn- reg-action--cursor-next [redakti]
  (reg-action
   redakti
   {:ident :cursor-next
    :f     #(!cursor % buffer/path-next)}))

(defn- reg-action--cursor-prev [redakti]
  (reg-action
   redakti
   {:ident :cursor-prev
    :f     #(!cursor % buffer/path-prev)}))

(defn- reg-action--cursor-up [redakti]
  (reg-action
   redakti
   {:ident :cursor-up
    :f     #(!cursor % buffer/path-up)}))

(defn- reg-action--cursor-down [redakti]
  (reg-action
   redakti
   {:ident :cursor-down
    :f     #(!cursor % buffer/path-down)}))

(defn- reg-action--cursor-left [redakti]
  (reg-action
   redakti
   {:ident :cursor-left
    :f     #(!cursor % buffer/path-left :goto-parent-buffer)}))

(defn- reg-action--cursor-right [redakti]
  (reg-action
   redakti
   {:ident :cursor-right
    :f     #(!cursor % buffer/path-right :enter)}))

(defn- reg-action--goto-parent-buffer [redakti]
  (reg-action
   redakti
   {:ident :goto-parent-buffer
    :f     (fn [redakti]
             (if-let [parent-buffer (-> redakti :buffer :parent-puffer)]
               (assoc redakti :buffer parent-buffer)
               (!message redakti :inf "Already in root buffer")))}))


(defn new-redakti [buffer]
  (-> {::identifier ::identifier
       :buffer buffer
       :actions {}
       :keymap {}}
      reg-action--cursor-step-in
      reg-action--cursor-step-out
      reg-action--cursor-prev
      reg-action--cursor-next
      reg-action--cursor-up
      (map-key "k" :cursor-up)
      reg-action--cursor-down
      (map-key "j" :cursor-down)
      reg-action--cursor-left
      (map-key "h" :cursor-left)
      reg-action--cursor-right
      (map-key "l" :cursor-right)
      reg-action--goto-parent-buffer
      (map-key "b" :goto-parent-buffer)
      (map-key "Enter" :enter)
      (map-key " " :menu)))


(defn !action [redakti action-ident]
  (let [redakti (dissoc redakti :message)]
    (if-let [action (or (-> redakti :actions (get action-ident))
                        (-> redakti :buffer :actions (get action-ident)))]
      (if-let [f (-> action :f)]
        (let [ret (f redakti)]
          (cond
            (nil? ret) redakti
            (keyword? ret) (!action redakti ret)
            (redakti? ret) ret
            :else (!message redakti :err (str "Action " action-ident " corrupted state!"))))
        (!message redakti :err (str "Missing :f in action " action-ident)))
      (!message redakti :inf (str "No action " action-ident)))))


(defn !action-for-key [redakti key]
  (if-let [action-ident (-> redakti :keymap (get key))]
    (!action redakti action-ident)
    (!message redakti :inf (str "No action associated with key [" key "]"))))
