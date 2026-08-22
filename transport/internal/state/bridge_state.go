package state

import (
	"sync"
	"time"
)

// BridgeState interface - implemented by internal/bridge.Bridge
type BridgeState interface {
	IsConnected() bool
	ReconnectCount() int64
	LastError() string
	IsCLIRunning() bool
	TriggerReconnect()
	StartCLI() error
	GetQRCode() string
	HealthScore() map[string]any
}

// Bridge state shared between api and bridge packages (exported)
var (
	BridgeConnected        bool
	BridgeConnectedSince   string
	BridgeLastDisconnect   string
	BridgeReconnectCount   int64
	BridgeLastWsPing       time.Time
	BridgeMsgQueueDepth    int64
	BridgeLastCmdLatency   time.Duration
	BridgeCmdCount         int64
	BridgeMinLatency       time.Duration
	BridgeMaxLatency       time.Duration
	BridgeSendFunc         func(text string, userID, contactID int64)
	SimplexCmd             func(cmd string) (map[string]any, error)
	GlobalChatHub          *ChatHub
	
	// Mutexes
	wsMu       sync.Mutex
	wsConn     interface{} // *websocket.Conn
	pendingMu  sync.Mutex
	pendingResp map[string]chan map[string]any
	msgBuffer  []string
	msgBufferMu sync.Mutex
)

type ChatMessage struct {
	ID        string
	From      string
	Text      string
	Timestamp string
	IsUser    bool
	ChatID    string
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

func (h *ChatHub) GetQRCode() string {
	return ""
}
