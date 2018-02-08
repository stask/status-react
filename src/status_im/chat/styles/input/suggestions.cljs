(ns status-im.chat.styles.input.suggestions
  (:require-macros [status-im.utils.styles :refer [defnstyle]])
  (:require [status-im.ui.components.styles :as common]
            [status-im.utils.platform :as platform]))

(def color-item-description-text "#99a0a6")
(def color-item-border "#e8eaeb")
(def item-height 52)

(defn item-suggestion-container [last?]
  {:flex-direction      :row
   :align-items         :center
   :height              item-height
   :padding-left        14
   :padding-right       14
   :border-bottom-color color-item-border
   :border-bottom-width (if last? 0 1)})

(def item-suggestion-name
  {:color     common/color-black
   :font-size 15})

(def item-suggestion-description
  {:flex         1
   :font-size    15
   :margin-left  10
   :color        color-item-description-text})
