(ns kunagi-base.auth.c-oauth-userinfos
  (:require
   [kunagi-base.utils :as utils]))


(defn process-userinfo
  [[_ {:keys [service
              userinfo]}]
   context]
  (let [sub (-> userinfo :sub)
        old-info (-> context
                     :projections
                     :oauth-userinfos
                     :service->sub->userinfo
                     (get service)
                     (get sub))
        userinfo (-> old-info
                     (merge userinfo)
                     (dissoc :aud :at_hash :exp :azp :iat))]
    (if (= userinfo old-info)
      nil
      {:events [[:oauth-userinfo-received
                 {:service service :userinfo userinfo}]]})))
