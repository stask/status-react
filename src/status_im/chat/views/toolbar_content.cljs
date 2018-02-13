(ns status-im.chat.views.toolbar-content
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [clojure.string :as string]
            [cljs-time.core :as t]
            [status-im.ui.components.react :as react]

            [status-im.i18n :as i18n]
            [status-im.chat.styles.screen :as st]
            [status-im.utils.datetime :as time]
            [status-im.utils.platform :refer [platform-specific]]
            [status-im.utils.gfycat.core :refer [generate-gfy]]
            [status-im.constants :refer [console-chat-id]]))

(defn- online-text [contact chat-id]
  (cond
    (= console-chat-id chat-id) (i18n/label :t/available)
    contact (let [last-online      (get contact :last-online)
                  last-online-date (time/to-date last-online)
                  now-date         (t/now)]
              (if (and (pos? last-online)
                       (<= last-online-date now-date))
                (time/time-ago last-online-date)
                (i18n/label :t/active-unknown)))
    :else (i18n/label :t/active-unknown)))

(defn- in-progress-text [{:keys [highestBlock currentBlock startBlock]}]
  (let [total      (- highestBlock startBlock)
        ready      (- currentBlock startBlock)
        percentage (if (zero? ready)
                     0
                     (->> (/ ready total)
                          (* 100)
                          (.round js/Math)))]

    (str (i18n/label :t/sync-in-progress) " " percentage "% " currentBlock)))

(defview last-activity [{:keys [online-text sync-state]}]
  [state [:get :sync-data]]
  [react/text {:style st/last-activity-text}
   (case sync-state
     :in-progress (in-progress-text state)
     :synced (i18n/label :t/sync-synced)
     online-text)])

(defn- group-last-activity [{:keys [contacts sync-state public?]}]
  (if (or (= sync-state :in-progress)
          (= sync-state :synced))
    [last-activity {:sync-state sync-state}]
    (if public?
      [react/view {:flex-direction :row}
       [react/text (i18n/label :t/public-group-status)]]
      [react/view {:flex-direction :row}
       [react/text {:style st/members}
        (if public?
          (i18n/label :t/public-group-status)
          (let [cnt (inc (count contacts))]
            (i18n/label-pluralize cnt :t/members-active)))]])))

(defview toolbar-content-view []
  (letsubs [group-chat    [:chat :group-chat]
            name          [:chat :name]
            chat-id       [:chat :chat-id]
            contacts      [:chat :contacts]
            public?       [:chat :public?]
            public-key    [:chat :public-key]
            show-actions? [:get-current-chat-ui-prop :show-actions?]
            accounts      [:get-accounts]
            contact       [:get-in [:contacts/contacts @chat-id]]
            sync-state    [:sync-state]
            creating?     [:get :accounts/creating-account?]]
    [react/view (st/chat-name-view (or (empty? accounts)
                                       show-actions?
                                       creating?))
     (let [chat-name (if (string/blank? name)
                       (generate-gfy public-key)
                       (or (i18n/get-contact-translated chat-id :name name)
                           (i18n/label :t/chat-name)))]
       [react/text {:style           st/chat-name-text
                    :number-of-lines 1
                    :font            :toolbar-title}
        (if public?
          (str "#" chat-name)
          chat-name)])
     (if group-chat
       [group-last-activity {:contacts   contacts
                             :public?    public?
                             :sync-state sync-state}]
       [last-activity {:online-text (online-text contact chat-id)
                       :sync-state  sync-state}])]))
