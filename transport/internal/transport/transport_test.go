// Package transport implements the WebSocket transport API
package transport

import (
	"testing"
	"time"
)


// TestConstantsCompile handles the TestConstantsCompile HTTP request.
func TestConstantsCompile(t *testing.T) {
	if defaultPort != 17001 {
		t.Errorf("expected 17001, got %d", defaultPort)
	}
	if maxPayloadSize != 100*1024*1024 {
		t.Errorf("expected %d, got %d", 100*1024*1024, maxPayloadSize)
	}
	if readTimeout != 30*time.Second {
		t.Errorf("expected 30s, got %v", readTimeout)
	}
}


// TestMsgTypes handles the TestMsgTypes HTTP request.
func TestMsgTypes(t *testing.T) {
	tests := []struct {
		msgType MsgType
		expect  string
	}{
		{MsgPing, "ping"},
		{MsgPong, "pong"},
		{MsgTrackReq, "track_req"},
		{MsgTrackResp, "track_resp"},
		{MsgFileReq, "file_req"},
		{MsgFileResp, "file_resp"},
		{MsgPieceReq, "piece_req"},
		{MsgPieceResp, "piece_resp"},
		{MsgHaveAnnounce, "have"},
	}
	for _, tt := range tests {
		if string(tt.msgType) != tt.expect {
			t.Errorf("expected %s, got %s", tt.expect, string(tt.msgType))
		}
	}
}


// TestPeerType handles the TestPeerType HTTP request.
func TestPeerType(t *testing.T) {
	p := Peer{
		ID:      "test-id",
		Addr:    "127.0.0.1:17001",
		Region:  "eu",
		Latency: 10,
	}
	if p.ID != "test-id" || p.Addr != "127.0.0.1:17001" || p.Region != "eu" || p.Latency != 10 {
		t.Error("Peer fields mismatch")
	}
}


// TestMessageType handles the TestMessageType HTTP request.
func TestMessageType(t *testing.T) {
	m := Message{
		Type:    MsgPing,
		Payload: []byte("hello"),
		From:    "test",
	}
	if m.Type != MsgPing || string(m.Payload) != "hello" || m.From != "test" {
		t.Error("Message fields mismatch")
	}
}


// TestNewTransferConfig handles the TestNewTransferConfig HTTP request.
func TestNewTransferConfig(t *testing.T) {
	// Test with custom port
	t1 := NewTransfer("/tmp/test-cache", 18001)
	if t1.port != 18001 {
		t.Errorf("expected 18001, got %d", t1.port)
	}
	// Test with zero port (should use default)
	t2 := NewTransfer("/tmp/test-cache", 0)
	if t2.port != defaultPort {
		t.Errorf("expected %d, got %d", defaultPort, t2.port)
	}
}


// TestStringsHasPrefix handles the TestStringsHasPrefix HTTP request.
func TestStringsHasPrefix(t *testing.T) {
	if !stringsHasPrefix("/foo/bar", "/foo") {
		t.Error("expected true")
	}
	if stringsHasPrefix("/foo/bar", "/bar") {
		t.Error("expected false")
	}
	if stringsHasPrefix("", "/") {
		t.Error("expected false for empty string")
	}
}


// TestAddPeer handles the TestAddPeer HTTP request.
func TestAddPeer(t *testing.T) {
	t1 := NewTransfer("/tmp/test-peers", 0)
	p := &Peer{ID: "peer1", Addr: "10.0.0.1:17001"}
	t1.AddPeer(p)

	t1.mu.Lock()
	got, ok := t1.peers["peer1"]
	t1.mu.Unlock()
	if !ok {
		t.Error("peer not found after AddPeer")
	}
	if got.Addr != "10.0.0.1:17001" {
		t.Errorf("expected 10.0.0.1:17001, got %s", got.Addr)
	}
}


// TestAddrBeforeStart handles the TestAddrBeforeStart HTTP request.
func TestAddrBeforeStart(t *testing.T) {
	t1 := NewTransfer("/tmp/test-addr", 0)
	addr := t1.Addr()
	if addr != ":17001" {
		t.Errorf("expected :17001, got %s", addr)
	}
}
