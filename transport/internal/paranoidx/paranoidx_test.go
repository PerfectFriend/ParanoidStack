// Package paranoidx implements the ParanoidX multi-layer proxy chain
package paranoidx

import (
	"testing"
)


// TestInitialStatus handles the TestInitialStatus HTTP request.
func TestInitialStatus(t *testing.T) {
	globalStatus.mu.Lock()
	// Reset layers to default state before test (VPN is optional, not pre-initialized)
	globalStatus.layers = map[Layer]*LayerStatus{
		LayerV2Ray:   {Layer: LayerV2Ray, Healthy: false},
		LayerTor:     {Layer: LayerTor, Healthy: false},
		LayerSimpleX: {Layer: LayerSimpleX, Healthy: false},
	}
	globalStatus.overall = false
	globalStatus.mu.Unlock()

	status := GetStatus()
	// VPN is optional — only 3 layers pre-initialized by default
	if len(status) != 3 {
		t.Fatalf("expected 3 layers (no VPN by default), got %d", len(status))
	}
	for layer, s := range status {
		if s.Healthy {
			t.Fatalf("layer %s should be unhealthy initially", layer)
		}
		if s.Layer != layer {
			t.Fatalf("layer name mismatch: %s vs %s", s.Layer, layer)
		}
	}
}


// TestSetLayerStatus handles the TestSetLayerStatus HTTP request.
func TestSetLayerStatus(t *testing.T) {
	SetLayerStatus(LayerV2Ray, true, 5, "v2ray is running")
	SetLayerStatus(LayerTor, true, 20, "tor connected")
	SetLayerStatus(LayerSimpleX, true, 0, "simplex bridge up")

	status := GetStatus()
	if !status[LayerV2Ray].Healthy {
		t.Fatal("expected V2Ray healthy")
	}
	if status[LayerV2Ray].LatencyMs != 5 {
		t.Fatalf("expected latency 5, got %d", status[LayerV2Ray].LatencyMs)
	}
	if status[LayerV2Ray].Message != "v2ray is running" {
		t.Fatalf("unexpected message: %s", status[LayerV2Ray].Message)
	}
	if status[LayerV2Ray].Connected == "" {
		t.Fatal("expected connected timestamp")
	}
}


// TestOverallHealthy handles the TestOverallHealthy HTTP request.
func TestOverallHealthy(t *testing.T) {
	SetLayerStatus(LayerV2Ray, true, 0, "ok")
	SetLayerStatus(LayerTor, true, 0, "ok")
	SetLayerStatus(LayerSimpleX, true, 0, "ok")

	if !IsOverallHealthy() {
		t.Fatal("expected overall healthy when required layers are healthy (VPN optional)")
	}

	SetLayerStatus(LayerV2Ray, false, 0, "v2ray down")
	if IsOverallHealthy() {
		t.Fatal("expected unhealthy when V2Ray down and no VPN fallback")
	}

	// V2Ray + VPN mutual fallback: at least one must be healthy
	SetLayerStatus(LayerVPN, true, 0, "vpn fallback ok")
	if !IsOverallHealthy() {
		t.Fatal("expected overall healthy when VPN covers for V2Ray")
	}

	// Both down = unhealthy
	SetLayerStatus(LayerVPN, false, 0, "vpn also down")
	if IsOverallHealthy() {
		t.Fatal("expected unhealthy when both V2Ray and VPN are down")
	}
}


// TestOverallHealthyWithVpnFallback handles the TestOverallHealthyWithVpnFallback HTTP request.
func TestOverallHealthyWithVpnFallback(t *testing.T) {
	SetLayerStatus(LayerV2Ray, false, 0, "v2ray down")
	SetLayerStatus(LayerVPN, true, 0, "vpn ok")
	SetLayerStatus(LayerTor, true, 0, "ok")
	SetLayerStatus(LayerSimpleX, true, 0, "ok")

	if !IsOverallHealthy() {
		t.Fatal("expected overall healthy: VPN fallback covers V2Ray")
	}
}


// TestOverallUnhealthyWhenBothVpnAndV2RayDown handles the TestOverallUnhealthyWhenBothVpnAndV2RayDown HTTP request.
func TestOverallUnhealthyWhenBothVpnAndV2RayDown(t *testing.T) {
	SetLayerStatus(LayerV2Ray, false, 0, "v2ray down")
	SetLayerStatus(LayerVPN, false, 0, "vpn down")
	SetLayerStatus(LayerTor, true, 0, "ok")
	SetLayerStatus(LayerSimpleX, true, 0, "ok")

	if IsOverallHealthy() {
		t.Fatal("expected overall unhealthy when both V2Ray and VPN are down")
	}
}


// TestGetOverallStatus handles the TestGetOverallStatus HTTP request.
func TestGetOverallStatus(t *testing.T) {
	// Reset to default state (no VPN)
	globalStatus.mu.Lock()
	globalStatus.layers = map[Layer]*LayerStatus{
		LayerV2Ray:   {Layer: LayerV2Ray},
		LayerTor:     {Layer: LayerTor},
		LayerSimpleX: {Layer: LayerSimpleX},
	}
	globalStatus.overall = false
	globalStatus.mu.Unlock()

	SetLayerStatus(LayerV2Ray, true, 10, "ok")
	SetLayerStatus(LayerTor, true, 25, "connected")
	SetLayerStatus(LayerSimpleX, true, 0, "bridge ok")

	overall := GetOverallStatus()
	if overall["overall_healthy"] != true {
		t.Fatal("expected overall_healthy true (VPN optional)")
	}
	rawLayers := overall["layers"].([]map[string]any)
	// VPN is optional — only added when explicitly SetLayerStatus'd
	if len(rawLayers) != 3 {
		t.Fatalf("expected 3 layers (no VPN by default), got %d", len(rawLayers))
	}

	if overall["last_updated"] == "" {
		t.Fatal("expected last_updated timestamp")
	}
}


// TestGetOverallStatusWithVpn handles the TestGetOverallStatusWithVpn HTTP request.
func TestGetOverallStatusWithVpn(t *testing.T) {
	SetLayerStatus(LayerV2Ray, true, 10, "ok")
	SetLayerStatus(LayerVPN, false, 0, "wg0 not configured")
	SetLayerStatus(LayerTor, true, 25, "connected")
	SetLayerStatus(LayerSimpleX, true, 0, "bridge ok")

	overall := GetOverallStatus()
	// V2Ray healthy → overall healthy (VPN is optional fallback)
	if overall["overall_healthy"] != true {
		t.Fatal("expected overall_healthy true: V2Ray covers VPN")
	}
	rawLayers := overall["layers"].([]map[string]any)
	if len(rawLayers) != 4 {
		t.Fatalf("expected 4 layers (VPN explicitly added), got %d", len(rawLayers))
	}

	foundVpn := false
	for _, l := range rawLayers {
		if l["layer"] == LayerVPN {
			foundVpn = true
			if l["healthy"] != false {
				t.Fatal("expected VPN unhealthy")
			}
			if l["message"] != "wg0 not configured" {
				t.Fatalf("unexpected VPN message: %s", l["message"])
			}
		}
	}
	if !foundVpn {
		t.Fatal("VPN layer not found in status")
	}
}


// TestSetLayerOverwritesPrevious handles the TestSetLayerOverwritesPrevious HTTP request.
func TestSetLayerOverwritesPrevious(t *testing.T) {
	SetLayerStatus(LayerV2Ray, true, 5, "first status")
	SetLayerStatus(LayerV2Ray, true, 10, "updated status")

	status := GetStatus()
	s := status[LayerV2Ray]
	if s.Message != "updated status" {
		t.Fatalf("expected 'updated status', got '%s'", s.Message)
	}
	if s.LatencyMs != 10 {
		t.Fatalf("expected latency 10, got %d", s.LatencyMs)
	}
}


// TestSetLayerStatusDifferentLayers handles the TestSetLayerStatusDifferentLayers HTTP request.
func TestSetLayerStatusDifferentLayers(t *testing.T) {
	SetLayerStatus(LayerV2Ray, true, 1, "vx")
	SetLayerStatus(LayerVPN, false, 0, "vp")
	SetLayerStatus(LayerTor, true, 2, "to")
	SetLayerStatus(LayerSimpleX, true, 3, "sx")

	status := GetStatus()
	if status[LayerV2Ray].Message != "vx" {
		t.Fatal("v2ray message wrong")
	}
	if status[LayerVPN].Message != "vp" {
		t.Fatal("vpn message wrong")
	}
	if status[LayerTor].Message != "to" {
		t.Fatal("tor message wrong")
	}
	if status[LayerSimpleX].Message != "sx" {
		t.Fatal("simplex message wrong")
	}
}


// TestOverallHealthyV2RayTorSimplexOnly handles the TestOverallHealthyV2RayTorSimplexOnly HTTP request.
func TestOverallHealthyV2RayTorSimplexOnly(t *testing.T) {
	// Only required layers: V2Ray, Tor, Simplex. VPN is optional.
	SetLayerStatus(LayerV2Ray, true, 0, "ok")
	SetLayerStatus(LayerTor, true, 0, "ok")
	SetLayerStatus(LayerSimpleX, true, 0, "ok")

	if !IsOverallHealthy() {
		t.Fatal("expected overall healthy with V2Ray, Tor, Simplex only")
	}
}


// TestStatusSnapshotNotAffectedByMutation handles the TestStatusSnapshotNotAffectedByMutation HTTP request.
func TestStatusSnapshotNotAffectedByMutation(t *testing.T) {
	SetLayerStatus(LayerV2Ray, true, 0, "before")
	status := GetStatus()
	SetLayerStatus(LayerV2Ray, false, 0, "after")

	// The snapshot should not be affected by subsequent changes
	if status[LayerV2Ray].Healthy != true {
		t.Fatal("snapshot should preserve previous state")
	}
	if status[LayerV2Ray].Message != "before" {
		t.Fatal("snapshot message should be 'before'")
	}
}


// TestGetOverallStatusHasTimestamp handles the TestGetOverallStatusHasTimestamp HTTP request.
func TestGetOverallStatusHasTimestamp(t *testing.T) {
	SetLayerStatus(LayerV2Ray, true, 0, "time test")
	overall := GetOverallStatus()
	ts := overall["last_updated"].(string)
	if ts == "" {
		t.Fatal("expected non-empty last_updated")
	}
	// Verify it's a valid RFC3339 string
	if len(ts) < 20 {
		t.Fatalf("timestamp too short: %q", ts)
	}
}
