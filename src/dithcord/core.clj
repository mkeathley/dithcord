(ns dithcord.core
  (:gen-class)
  (:require [clojure.core.async :as async :refer :all :as async]
            [clj-http.client :as http]
            [gniazdo.core :as ws]
            [cheshire.core :refer [parse-string generate-string]]))

(def API "https://discordapp.com/api/v6")
(def CDN "https://cdn.discordapp.com")

(def endpoints
  {:login               (str API "/auth/login")
   :logout              (str API "/auth/logout")
   :gateway             (str API "/gateway")
   :invite              (str API "/invite" :id)
   :CDN                 (str API "/auth/login")

   :user                #(str API "/users/" %1)
   :user-channels       #(str ((:user endpoints) %1) "/channels")
   :avatar              #(str ((:user endpoints) %1) "/avatars/" %2 ".jpg")
   :user-profile        (str API "/users/@me")
   :user-guilds         #(str ((:user-profile endpoints) "/guilds/" %1))

   :guilds              (str API "/guilds")
   :guild               #(str (:guilds endpoints) "/" %)
   :guild-icon          #(str ((:guild endpoints) %1) "/icons/" %2 ".jpg")
   :guild-prune         #(str ((:guild endpoints) %1) "/prune")
   :guild-embed         #(str ((:guild endpoints) %1) "/embed")
   :guild-invites       #(str ((:guild endpoints) %1) "/invites")
   :guild-roles         #(str ((:guilds endpoints) %1) "/roles")
   :guild-role          #(str ((:guilds-roles endpoints) %1) "/" %2)
   :guild-bans          #(str ((:guild endpoints) %1) "/bans")
   :guild-integrations  #(str ((:guild endpoints) %1) "/integrations")
   :guild-members       #(str ((:guild endpoints) %1) "/members")
   :guild-member        #(str ((:guilds-members endpoints) %1) "/" %2)
   :guild-member-nick   #(str ((:guild-member endpoints) %1 "@me") "/nick")
   ;WTF is this endpoint just for nickname, guys?

   :channels            (str API "/channels")
   :channel             #(str ((:channels endpoints) %1))
   :channel-messages    #(str ((:channel endpoints) %1) "/messages")
   :channel-message     #(str ((:channel-messages endpoints) %1) "/" %2)
   :channel-invites     #(str ((:channel endpoints) %1) "/invites")
   :channel-typing      #(str ((:channel endpoints) %1) "/typing")
   :channel-permissions #(str ((:channel endpoints) %1) "/permissions")})

(def error-messages
  {:NO_TOKEN                  "request to use token, but token was unavailable to the client"
   :NO_BOT_ACCOUNT            "you should ideally be using a bot account!"
   :BAD_WS_MESSAGE            "a bad message was received from the websocket - bad compression or not json"
   :TOOK_TOO_LONG             "something took too long to do"
   :NOT_A_PERMISSION          "that is not a valid permission string or number"
   :INVALID_RATE_LIMIT_METHOD "unknown rate limiting method"
   :BAD_LOGIN                 "incorrect login details were provided"})

;((:guild-icon endpoints) "the-guild-id" "the-icon-hash")

(def status
  {0 "READY"
   1 "CONNECTING"
   3 "IDLE"
   4 "NEARLY"})

(def channel-type
  {0 "text"
   1 "DM"
   2 "voice"
   3 "groupDM"})

(def op-codes
  {0 "DISPATCH"
   1 "HEARTBEAT"
   2 "IDENTIFY"
   3 "STATUS_UPDATE"
   4 "VOICE_STATUS_UPDATE"
   5 "VOICE_GUILD_PING"
   6 "RESUME"
   7 "RECONNECT"
   8 "REQUEST_GUILD_MEMBERS"
   9 "INVALID SESSION"})

(def voice-op-codes
  {0 "IDENTIFY"
   1 "SELECT_PROTOCOL"
   2 "READY"
   3 "HEARTBEAT"
   4 "SESSION_DESCRIPTION"
   5 "SPEAKING"})

(defn identify [token]
  {:token token
   :properties {
                 :$os "linux"
                 :$browser "dithcord"
                 :$device "dithcord"
                 :$referrer ""
                 :$referring_domain ""
                 }
   :compress true
   :large_threshold 250
   :shard 1
   })


(def core-in (async/chan))
(def core-out (async/chan))

;(defn log [msg]
;  (async/>!! core-in msg))
;(log "test")

(defn handle-error
  [error]
  println error)

(defn ping-pong [out-pipe delay]
  (let
    [counter (atom 0)
     next-id (#(swap! counter inc))]
    (go-loop []
      (async/<! (async/timeout delay))
      (printf "Sending a ping request")
      (async/>! out-pipe {:id (next-id) :type :ping})
      (recur))))

; main thread?
(async/thread
  (loop []
    (when-let [m (async/<!! core-in)]
      (let [op (:op m)]
        (case op
          [10] (ping-pong core-out (:interval (:d m))) ))
      (println (str "OP Code Received: " (:op m)))
      (println m)
      (recur)))
  (println "Log Closed"))

(defn connect-socket [url]
  (let [
        shutdown (fn []
          (async/close! core-in)
          (async/close! core-out))
        socket (ws/connect url
          :on-receive
            (fn [m] (async/put! core-in (parse-string m true)))
          :on-connect
            (fn [] (prn "Connected!") )
          :on-error
            (fn [_] (shutdown)))]
    (go-loop []
      (let [m (async/<! core-out)
            s (generate-string m)]
        (ws/send-msg socket s)
        (recur)))
    [core-in core-out]))

(defn start-socket []
  (let [ws-address (:url (:body (http/get (str API "/gateway") {:as :json})))]
    (connect-socket (str ws-address "/?v=6&encoding=json"))))

(connect-socket "wss://gateway.discord.gg/?v=6&encoding=json")
;(ws/send-msg socket (generate-string (identify "MjA5MDE1MzEwNTcxNzk4NTM0.CsEAEQ.1EWIOuraD_ZX44SEn2D6FHMlEfA"))

;(def socket
;  (ws/connect
;    "wss://gateway.discord.gg"
;    :on-receive
;    (fn [message] (printf message))))

;(def socket
;  (ws/connect "wss://gateway.discord.gg/?v=6&encoding=json"
;    :on-receive
;        (fn [m]
;          (prn 'received m)
;          ))
;  )

;(ws/send-msg socket "hello")
(ws/close socket)

(defn foo
  "I don't do a whole lot ... yet."
  [x]
  (println x "Hello, World!"))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
