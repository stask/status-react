(ns status-im.chat.views.input.send-button
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [clojure.string :as string]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [status-im.chat.styles.input.input :as style]
            [status-im.ui.components.animation :as anim]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.icons.vector-icons :as vi]
            [status-im.utils.utils :as utils]
            [taoensso.timbre :as log]))

(defn send-button-view-on-update [{:keys [anim-value command-completion]}]
  (fn [_]
    (let [to-value (if (some #{:complete :no-command} [@command-completion]) 1 0)]
      (anim/start
        (anim/spring anim-value {:toValue to-value})))))

(defview send-button-view []
  (letsubs [command-completion [:command-completion]
            selected-command   [:selected-chat-command]
            input-text         [:chat :input-text]
            seq-arg-input-text [:chat :seq-argument-input-text]
            anim-value         (anim/create-value 1)
            on-update          (send-button-view-on-update {:anim-value         anim-value
                                                            :command-completion command-completion})]
    {:component-did-update on-update}
    (let [{:keys [hide-send-button sequential-params]} (:command selected-command)]
      (when (and (not (string/blank? input-text))
                 (or (not selected-command)
                     (some #{:complete :less-than-needed} [command-completion]))
                 (not hide-send-button))
        [react/touchable-highlight {:on-press #(if sequential-params
                                                 (do
                                                   (when-not (string/blank? seq-arg-input-text)
                                                     (re-frame/dispatch [:send-seq-argument]))
                                                   (utils/set-timeout
                                                     (fn [] (re-frame/dispatch [:chat-input-focus :seq-input-ref]))
                                                     100))
                                                 (re-frame/dispatch [:send-current-message]))}
         (let [spin (.interpolate anim-value (clj->js {:inputRange  [0 1]
                                                       :outputRange ["0deg" "90deg"]}))]
           [react/animated-view
            {:style               (style/send-message-container spin)
             :accessibility-label :send-message-button}
            [vi/icon :icons/input-send {:container-style style/send-message-icon
                                        :color           :white}]])]))))