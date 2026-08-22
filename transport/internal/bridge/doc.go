// Package bridge implements the WebSocket connection to the simplex-chat CLI.
//
// Architecture:
//
//	px-transport (Go)  ←→  WebSocket (127.0.0.1:17225)  ←→  simplex-chat CLI
//
// Protocol: JSON messages with corrId (correlation ID) and cmd (command type).
// The bridge auto-reconnects with a safe loop pattern (not recursive) and
// exposes health metrics including connection latency and reconnect count.
//
// Key components:
//   - Bridge struct:    Main bridge state (conn, reconnect loop, health)
//   - Run():            Connection loop with built-in reconnection
//   - Send():           Send command to simplex-chat via WebSocket
//   - handleMessage():  Process incoming messages and route to subscribers
//   - ChatHub:          Message broker between HTTP handlers and the bridge
package bridge
