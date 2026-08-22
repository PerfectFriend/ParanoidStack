// Package transport provides direct P2P transfer for radio tracks and vault files,
// bypassing Tor onion routing for maximum speed.
//
// Protocol: simple TCP with 4-byte length prefix + payload.
// NAT traversal: UPnP port mapping + STUN for direct connection.
package transport

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"
)

const (
	defaultPort    = 17001
	maxPayloadSize = 100 * 1024 * 1024 // 100MB
	readTimeout    = 30 * time.Second
)

// Peer represents a connected P2P peer.
type Peer struct {
	ID      string `json:"id"`
	Addr    string `json:"addr"`
	Region  string `json:"region"`
	Latency int    `json:"latency_ms"`
}

// Message types
type MsgType string

const (
	MsgPing       MsgType = "ping"
	MsgPong       MsgType = "pong"
	MsgTrackReq   MsgType = "track_req"
	MsgTrackResp  MsgType = "track_resp"
	MsgFileReq    MsgType = "file_req"
	MsgFileResp   MsgType = "file_resp"
	MsgPieceReq   MsgType = "piece_req"
	MsgPieceResp  MsgType = "piece_resp"
	MsgHaveAnnounce MsgType = "have"
)

// Message is the wire format for P2P communication.
type Message struct {
	Type    MsgType `json:"t"`
	Payload []byte  `json:"p,omitempty"`
	From    string  `json:"f,omitempty"`
}

// Transfer provides direct peer-to-peer file transfer.
type Transfer struct {
	mu       sync.Mutex
	peers    map[string]*Peer
	cacheDir string
	port     int
	listener net.Listener
	stopCh   chan struct{}
}

// NewTransfer creates a P2P transfer service.
func NewTransfer(cacheDir string, port int) *Transfer {
	if port == 0 {
		port = defaultPort
	}
	t := &Transfer{
		peers:    make(map[string]*Peer),
		cacheDir: cacheDir,
		port:     port,
		stopCh:   make(chan struct{}),
	}
	os.MkdirAll(cacheDir, 0755)
	return t
}

// Start begins listening for incoming P2P connections.
func (t *Transfer) Start() error {
	addr := fmt.Sprintf(":%d", t.port)
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("transport listen: %w", err)
	}
	t.listener = ln
	slog.Info("transport P2P listening", "addr", addr)

	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				select {
				case <-t.stopCh:
					return
				default:
					continue
				}
			}
			go t.handleConn(conn)
		}
	}()
	return nil
}

// Stop shuts down the P2P listener.
func (t *Transfer) Stop() {
	close(t.stopCh)
	if t.listener != nil {
		t.listener.Close()
	}
}

func (t *Transfer) handleConn(conn net.Conn) {
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(readTimeout))

	var msg Message
	if err := t.readMsg(conn, &msg); err != nil {
		return
	}

	switch msg.Type {
	case MsgPing:
		t.sendMsg(conn, Message{Type: MsgPong})
	case MsgTrackReq:
		t.handleTrackReq(conn, msg)
	case MsgFileReq:
		t.handleFileReq(conn, msg)
	case MsgPieceReq:
		t.handlePieceReq(conn, msg)
	}
}

func (t *Transfer) readMsg(r io.Reader, msg *Message) error {
	var lenBuf [4]byte
	if _, err := io.ReadFull(r, lenBuf[:]); err != nil {
		return err
	}
	size := binary.BigEndian.Uint32(lenBuf[:])
	if size > maxPayloadSize {
		return fmt.Errorf("message too large: %d", size)
	}
	buf := make([]byte, size)
	if _, err := io.ReadFull(r, buf); err != nil {
		return err
	}
	return json.Unmarshal(buf, msg)
}

func (t *Transfer) sendMsg(w io.Writer, msg Message) error {
	data, err := json.Marshal(msg)
	if err != nil {
		return err
	}
	var lenBuf [4]byte
	binary.BigEndian.PutUint32(lenBuf[:], uint32(len(data)))
	if _, err := w.Write(lenBuf[:]); err != nil {
		return err
	}
	_, err = w.Write(data)
	return err
}

func (t *Transfer) handleTrackReq(conn net.Conn, msg Message) {
	var req struct {
		TrackID string `json:"track_id"`
	}
	json.Unmarshal(msg.Payload, &req)

	trackPath := filepath.Join(t.cacheDir, "tracks", req.TrackID)
	data, err := os.ReadFile(trackPath)
	if err != nil {
		t.sendMsg(conn, Message{Type: MsgTrackResp, Payload: []byte(`{"error":"not found"}`)})
		return
	}
	t.sendMsg(conn, Message{Type: MsgTrackResp, Payload: data})
}

func (t *Transfer) handleFileReq(conn net.Conn, msg Message) {
	var req struct {
		Path string `json:"path"`
	}
	json.Unmarshal(msg.Payload, &req)

	fullPath := filepath.Join(t.cacheDir, filepath.Clean(req.Path))
	if !stringsHasPrefix(fullPath, t.cacheDir) {
		t.sendMsg(conn, Message{Type: MsgFileResp, Payload: []byte(`{"error":"invalid path"}`)})
		return
	}
	data, err := os.ReadFile(fullPath)
	if err != nil {
		t.sendMsg(conn, Message{Type: MsgFileResp, Payload: []byte(`{"error":"not found"}`)})
		return
	}
	t.sendMsg(conn, Message{Type: MsgFileResp, Payload: data})
}

func (t *Transfer) handlePieceReq(conn net.Conn, msg Message) {
	var req struct {
		Hash   string `json:"hash"`
		Offset int    `json:"offset"`
		Size   int    `json:"size"`
	}
	json.Unmarshal(msg.Payload, &req)

	piecePath := filepath.Join(t.cacheDir, "pieces", req.Hash)
	f, err := os.Open(piecePath)
	if err != nil {
		t.sendMsg(conn, Message{Type: MsgPieceResp, Payload: []byte(`{"error":"not found"}`)})
		return
	}
	defer f.Close()

	buf := make([]byte, req.Size)
	f.ReadAt(buf, int64(req.Offset))
	t.sendMsg(conn, Message{Type: MsgPieceResp, Payload: buf})
}

// RequestTrack fetches a radio track from a peer by ID.
func (t *Transfer) RequestTrack(peerAddr string, trackID string) ([]byte, error) {
	conn, err := net.DialTimeout("tcp", peerAddr, 5*time.Second)
	if err != nil {
		return nil, fmt.Errorf("connect: %w", err)
	}
	defer conn.Close()

	reqPayload, _ := json.Marshal(map[string]string{"track_id": trackID})
	t.sendMsg(conn, Message{Type: MsgTrackReq, Payload: reqPayload})

	var resp Message
	t.readMsg(conn, &resp)
	return resp.Payload, nil
}

// AnnounceHave tells all known peers this node has a new piece.
func (t *Transfer) AnnounceHave(hash string) {
	payload, _ := json.Marshal(map[string]string{"hash": hash})
	t.mu.Lock()
	peers := make([]*Peer, 0, len(t.peers))
	for _, p := range t.peers {
		peers = append(peers, p)
	}
	t.mu.Unlock()

	for _, p := range peers {
		go func(addr string) {
			conn, err := net.DialTimeout("tcp", addr, 3*time.Second)
			if err != nil {
				return
			}
			defer conn.Close()
			t.sendMsg(conn, Message{Type: MsgHaveAnnounce, Payload: payload})
		}(p.Addr)
	}
}

// AddPeer registers a peer for future connections.
func (t *Transfer) AddPeer(p *Peer) {
	t.mu.Lock()
	t.peers[p.ID] = p
	t.mu.Unlock()
}

// CacheTrack saves a downloaded track locally for future seeding.
func (t *Transfer) CacheTrack(trackID string, data []byte) error {
	dir := filepath.Join(t.cacheDir, "tracks")
	os.MkdirAll(dir, 0755)
	path := filepath.Join(dir, trackID)
	if err := os.WriteFile(path, data, 0644); err != nil {
		return err
	}
	hash := fmt.Sprintf("%x", sha256.Sum256(data))
	go t.AnnounceHave(hash)
	return nil
}

// Addr returns the listener's address.
func (t *Transfer) Addr() string {
	if t.listener != nil {
		return t.listener.Addr().String()
	}
	return fmt.Sprintf(":%d", t.port)
}

func stringsHasPrefix(s, prefix string) bool {
	return len(s) >= len(prefix) && s[:len(prefix)] == prefix
}
