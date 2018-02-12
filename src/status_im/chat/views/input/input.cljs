(ns status-im.chat.views.input.input
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as re-frame :refer [subscribe dispatch]]
            [taoensso.timbre :as log]
            [status-im.chat.constants :as const]
            [status-im.chat.models.input :as input-model]
            [status-im.chat.models.commands :as commands-model]
            [status-im.chat.styles.input.input :as style]
            [status-im.chat.views.input.parameter-box :as parameter-box]
            [status-im.chat.views.input.result-box :as result-box]
            [status-im.chat.views.input.suggestions :as suggestions]
            [status-im.chat.views.input.validation-messages :as validation-messages]
            [status-im.ui.components.animation :as anim]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.icons.vector-icons :as vi]
            [status-im.i18n :as i18n]
            [status-im.utils.platform :as platform]
            [status-im.utils.utils :as utils]
            [clojure.string :as string]))

(defn- basic-text-input [_]
  (let [input-text     (subscribe [:chat :input-text])
        command        (subscribe [:selected-chat-command])
        input-focused? (subscribe [:get-current-chat-ui-prop :input-focused?])
        input-ref      (atom nil)]
    (fn [{:keys [set-layout-height-fn set-container-width-fn height single-line-input?]}]
      [react/text-input
       {:ref                    #(when %
                                   (dispatch [:set-chat-ui-props {:input-ref %}])
                                   (reset! input-ref %))
        :accessibility-label    :chat-message-input
        :multiline              (not single-line-input?)
        :default-value          (or @input-text "")
        :editable               true
        :blur-on-submit         false
        :on-focus               #(dispatch [:set-chat-ui-props {:input-focused? true}])
        :on-blur                #(dispatch [:set-chat-ui-props {:input-focused? false}])
        :on-submit-editing      (fn [e]
                                  (if single-line-input?
                                    (dispatch [:send-current-message])
                                    (.setNativeProps @input-ref (clj->js {:text (str @input-text "\n")}))))
        :on-layout              (fn [e]
                                  (set-container-width-fn (.-width (.-layout (.-nativeEvent e)))))
        :on-change              (fn [e]
                                  (let [native-event (.-nativeEvent e)
                                        text         (.-text native-event)
                                        content-size (.. native-event -contentSize)]
                                    (when (and (not single-line-input?)
                                               content-size)
                                      (set-layout-height-fn (.-height content-size)))
                                    (when (not= text @input-text)
                                      (dispatch [:set-chat-input-text text])
                                      (when @command
                                        (dispatch [:load-chat-parameter-box (:command @command)]))
                                      (dispatch [:update-input-data]))))
        :on-content-size-change (when (and (not @input-focused?)
                                           (not single-line-input?))
                                  #(let [h (-> (.-nativeEvent %)
                                               (.-contentSize)
                                               (.-height))]
                                     (set-layout-height-fn h)))
        :on-selection-change    #(let [s   (-> (.-nativeEvent %)
                                               (.-selection))
                                       end (.-end s)]
                                   (dispatch [:update-text-selection end]))
        :style                  (style/input-view height single-line-input?)
        :placeholder-text-color style/color-input-helper-placeholder
        :auto-capitalize        :sentences}])))

(defn- invisible-input [{:keys [set-layout-width-fn value]}]
  (let [input-text    (subscribe [:chat :input-text])]
    [react/text {:style     style/invisible-input-text
                 :on-layout #(let [w (-> (.-nativeEvent %)
                                         (.-layout)
                                         (.-width))]
                               (set-layout-width-fn w))}
     (or @input-text "")]))

(defview invisible-input-height [{:keys [set-layout-height-fn container-width]}]
  (letsubs [input-text [:chat :input-text]]
    [react/text {:style     (style/invisible-input-text-height container-width)
                 :on-layout #(let [h (-> (.-nativeEvent %)
                                         (.-layout)
                                         (.-height))]
                               (set-layout-height-fn h))}
     (or input-text "")]))

(defview input-helper [{:keys [command width]}]
  (letsubs [input-text [:chat :input-text]]
    (when (and (string/ends-with? (or input-text "") const/spacing-char)
               (not (get-in command [:command :sequential-params])))
      (let [input     (str/trim (or input-text ""))
            real-args (remove str/blank? (:args command))]
        (when-let [placeholder (cond
                                 (and command (empty? real-args))
                                 (get-in command [:command :params 0 :placeholder])

                                 (and command
                                      (= (count real-args) 1)
                                      (input-model/text-ends-with-space? input))
                                 (get-in command [:command :params 1 :placeholder]))]
          [react/text {:style (style/input-helper-text width)}
           placeholder])))))

(defn get-options [type]
  (case (keyword type)
    :phone {:keyboard-type "phone-pad"}
    :password {:secure-text-entry true}
    :number {:keyboard-type "numeric"}
    nil))

(defn- seq-input [_]
  (let [command            (subscribe [:selected-chat-command])
        arg-pos            (subscribe [:current-chat-argument-position])
        seq-arg-input-text (subscribe [:chat :seq-argument-input-text])]
    (fn [{:keys [command-width container-width]}]
      (when (get-in @command [:command :sequential-params])
        (let [{:keys [placeholder hidden type]} (get-in @command [:command :params @arg-pos])]
          [react/text-input (merge {:ref                 #(dispatch [:set-chat-ui-props {:seq-input-ref %}])
                                    :style               (style/seq-input-text command-width container-width)
                                    :default-value       (or @seq-arg-input-text "")
                                    :on-change-text      #(do (dispatch [:set-chat-seq-arg-input-text %])
                                                              (dispatch [:load-chat-parameter-box (:command @command)])
                                                              (dispatch [:set-chat-ui-props {:validation-messages nil}]))
                                    :placeholder         placeholder
                                    :accessibility-label :chat-request-input
                                    :blur-on-submit      false
                                    :editable            true
                                    :on-submit-editing   (fn []
                                                           (when-not (or (str/blank? @seq-arg-input-text)
                                                                         (get-in @command [:command :hide-send-button]))
                                                             (dispatch [:send-seq-argument]))
                                                           (utils/set-timeout
                                                             #(dispatch [:chat-input-focus :seq-input-ref])
                                                             100))}
                                   (get-options type))])))))

(defview input-view [{:keys [single-line-input?]}]
  (letsubs [command [:selected-chat-command]]
    (let [component              (r/current-component)
          set-layout-width-fn    #(r/set-state component {:width %})
          set-layout-height-fn   #(r/set-state component {:height %})
          set-container-width-fn #(r/set-state component {:container-width %})
          {:keys [width height container-width]} (r/state component)]
      [react/view {:style style/input-root}
       [react/animated-view {:style (style/input-animated height)}
        [invisible-input {:set-layout-width-fn set-layout-width-fn}]
        [invisible-input-height {:set-layout-height-fn set-layout-height-fn
                                 :container-width      container-width}]
        [basic-text-input {:set-layout-height-fn   set-layout-height-fn
                           :set-container-width-fn set-container-width-fn
                           :height                 height
                           :single-line-input?     single-line-input?}]
        [input-helper {:command command
                       :width   width}]
        [seq-input {:command-width   width
                    :container-width container-width}]]])))

(defn commands-button []
  [react/touchable-highlight
   {:on-press #(do (re-frame/dispatch [:set-chat-input-text const/command-char])
                   (react/dismiss-keyboard!))}
   [react/view
    [vi/icon :icons/input-commands {:container-style style/input-commands-icon}]]])

(defview send-button []
  (letsubs [command-completion [:command-completion]
            selected-command   [:selected-chat-command]
            input-text         [:chat :input-text]
            seq-arg-input-text [:chat :seq-argument-input-text]]
    (let [{:keys [hide-send-button sequential-params]} (:command selected-command)]
      (when (and (not (str/blank? input-text))
                 (or (not selected-command)
                     (some #{:complete :less-than-needed} [command-completion]))
                 (not hide-send-button))
        [react/touchable-highlight {:on-press #(if sequential-params
                                                 (do
                                                   (when-not (str/blank? seq-arg-input-text)
                                                     (dispatch [:send-seq-argument]))
                                                   (utils/set-timeout
                                                     (fn [] (dispatch [:chat-input-focus :seq-input-ref]))
                                                     100))
                                                 (dispatch [:send-current-message]))}
         [react/view {:style               style/send-message-container
                      :accessibility-label :send-message-button}
          [vi/icon :icons/input-send {:container-style style/send-message-icon
                                      :color           :white}]]]))))

(defview input-container []
  (letsubs [margin     [:chat-input-margin]
            input-text [:chat :input-text]
            result-box [:get-current-chat-ui-prop :result-box]]
    (let [single-line-input? (:singleLineInput result-box)]
      [react/view {:style     (style/root margin)
                   :on-layout #(let [h (-> (.-nativeEvent %)
                                           (.-layout)
                                           (.-height))]
                                 (when (> h 0)
                                   (dispatch [:set-chat-ui-props {:input-height h}])))}
       [react/view {:style style/input-container}
        [input-view {:single-line-input? single-line-input?}]
        (when (string/blank? input-text)
          [commands-button])
        [send-button]]])))

(defn container []
  [react/view style/input-container-view
   [parameter-box/parameter-box-view]
   [result-box/result-box-view]
   [suggestions/suggestions-view]
   [validation-messages/validation-messages-view]
   [input-container]])
