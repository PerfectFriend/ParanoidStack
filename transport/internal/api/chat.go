package api

import (
	"encoding/json"
	"net/http"
	"sync"
	"time"

	"px-transport/internal/common"
	"px-transport/internal/state"
)

type ChatMessage struct {
	ID        string    `json:"id"`
	From      string    `json:"from"`
	Text      string    `json:"text"`
	Timestamp time.Time `json:"timestamp"`
	IsUser    bool      `json:"is_user"`
	ChatID    string    `json:"chat_id"`
}

type ChatHub struct {
	mu   sync.RWMutex
	subs map[string]chan ChatMessage
}

func NewChatHub() *ChatHub {
	return &ChatHub{
		subs: make(map[string]chan ChatMessage),
	}
}

func (h *ChatHub) Subscribe(id string) chan ChatMessage {
	h.mu.Lock()
	defer h.mu.Unlock()
	ch := make(chan ChatMessage, 100)
	h.subs[id] = ch
	return ch
}

func (h *ChatHub) Unsubscribe(id string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.subs, id)
}

func (h *ChatHub) AddMessage(msg ChatMessage) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for _, ch := range h.subs {
		select {
		case ch <- msg:
		default:
		}
	}
}

type ChatHandler struct {
	hub     *ChatHub
	mu      sync.Mutex
	history []ChatMessage
}

func NewChatHandler() *ChatHandler {
	return &ChatHandler{
		hub: NewChatHub(),
	}
}

func (h *ChatHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch r.URL.Path {
	case "/api/chat/status":
		h.status(w, r)
	case "/api/chat/history":
		h.historyHandler(w, r)
	case "/api/chat/stream":
		h.streamHandler(w, r)
	case "/api/chat/send":
		h.sendHandler(w, r)
	case "/api/chat/contacts":
		h.contactsHandler(w, r)
	case "/api/chat/address":
		h.addressHandler(w, r)
	case "/api/chat/qr":
		h.qrHandler(w, r)
	default:
		http.NotFound(w, r)
	}
}

func (h *ChatHandler) status(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{
		"bridge_connected": state.BridgeConnected,
		"reconnect_count":  state.BridgeReconnectCount,
		"message_count":    len(h.history),
		"bridge_since":     state.BridgeConnectedSince,
		"last_disconnect":  state.BridgeLastDisconnect,
	})
}

func (h *ChatHandler) historyHandler(w http.ResponseWriter, r *http.Request) {
	h.mu.Lock()
	defer h.mu.Unlock()
	common.WriteJSON(w, map[string]any{"history": h.history})
}

func (h *ChatHandler) streamHandler(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming unsupported", 500)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	ch := h.hub.Subscribe("stream")
	defer h.hub.Unsubscribe("stream")

	for {
		select {
		case msg := <-ch:
			data, _ := json.Marshal(msg)
			w.Write([]byte("data: " + string(data) + "\n\n"))
			flusher.Flush()
		case <-r.Context().Done():
			return
		}
	}
}

func (h *ChatHandler) sendHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		http.Error(w, "POST required", 405)
		return
	}
	var req struct {
		ChatID string `json:"chat_id"`
		Text   string `json:"text"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad request", 400)
		return
	}
	if state.SimplexCmd != nil {
		_, err := state.SimplexCmd("/_send @" + req.ChatID + " " + req.Text)
		if err != nil {
			common.WriteJSON(w, map[string]any{"ok": false, "error": err.Error()})
			return
		}
	}
	common.WriteJSON(w, map[string]any{"ok": true})
}

func (h *ChatHandler) contactsHandler(w http.ResponseWriter, r *http.Request) {
	if state.SimplexCmd != nil {
		resp, err := state.SimplexCmd("/_contacts 1")
		if err == nil {
			common.WriteJSON(w, resp)
			return
		}
	}
	common.WriteJSON(w, map[string]any{"contacts": []any{}})
}

func (h *ChatHandler) addressHandler(w http.ResponseWriter, r *http.Request) {
	if state.SimplexCmd != nil {
		resp, err := state.SimplexCmd("/_address")
		if err == nil {
			common.WriteJSON(w, resp)
			return
		}
	}
	common.WriteJSON(w, map[string]any{"address": ""})
}

func (h *ChatHandler) qrHandler(w http.ResponseWriter, r *http.Request) {
	qr := state.GlobalChatHub.GetQRCode()
	common.WriteJSON(w, map[string]any{"qr": qr})
}
