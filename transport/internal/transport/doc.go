// Package transport implements the transport hub for third-party app integration
// with the px-transport messaging system. It manages app registration with API key
// authentication, WebSocket connections with reconnect tokens, rate-limited message
// delivery, contact routing, message history replay, webhook push, and audit logging.
// Key types include Hub (Register, Authenticate, Subscribe, Broadcast, SendThroughBridge),
// App, WSMessage, and AuditEntry.
package transport
