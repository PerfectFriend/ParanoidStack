// Package api — chat hub integration tests, aligned with the real ChatHub
// API (pub/sub fan-out). The original test file referenced a message-store
// API (GetMessages/EditMessageText/DeleteMessage/ClearMessages/WithFile and
// a Status enum) that was never implemented; it broke the package build.
package api

import (
	"testing"
	"time"
)

func TestChatHubSubscribeFanOut(t *testing.T) {
	hub := NewChatHub()

	ch1 := hub.Subscribe("sub-1")
	ch2 := hub.Subscribe("sub-2")
	defer func() {
		hub.Unsubscribe("sub-1")
		hub.Unsubscribe("sub-2")
	}()

	msg := ChatMessage{
		ID:        "msg-test-1",
		From:      "alice",
		Text:      "hello world",
		Timestamp: time.Now().UTC(),
		IsUser:    true,
		ChatID:    "@1",
	}
	hub.AddMessage(msg)

	for name, ch := range map[string]<-chan ChatMessage{"sub-1": ch1, "sub-2": ch2} {
		select {
		case got := <-ch:
			if got.ID != msg.ID || got.Text != msg.Text {
				t.Fatalf("%s: wrong message delivered: %+v", name, got)
			}
		case <-time.After(2 * time.Second):
			t.Fatalf("%s: timed out waiting for broadcast", name)
		}
	}
}

func TestChatHubUnsubscribeStopsDelivery(t *testing.T) {
	hub := NewChatHub()
	ch := hub.Subscribe("solo")
	hub.Unsubscribe("solo")

	hub.AddMessage(ChatMessage{ID: "x", Text: "after unsub"})

	select {
	case got := <-ch:
		t.Fatalf("expected no delivery after unsubscribe, got %+v", got)
	case <-time.After(200 * time.Millisecond):
		// ok — nothing arrived
	}
}
