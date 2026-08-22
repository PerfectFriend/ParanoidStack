// Package dockerutil tests — aligned with the real ApplyStatus semantics:
// it copies the pointed-at status strings into the svc map (it does NOT
// derive them from svc["name"]; that was what the old tests wrongly assumed).
package dockerutil

import (
	"testing"
)

func TestApplyStatusCopiesBoth(t *testing.T) {
	smp := "Up 2 hours"
	xftp := "Not running"
	svc := map[string]any{}

	ApplyStatus(&smp, &xftp, svc)

	if svc["smp"] != "Up 2 hours" {
		t.Fatalf("expected smp copied, got %v", svc["smp"])
	}
	if svc["xftp"] != "Not running" {
		t.Fatalf("expected xftp copied, got %v", svc["xftp"])
	}
}

func TestApplyStatusNilLeavesMapUntouched(t *testing.T) {
	svc := map[string]any{"smp": "keep"}

	ApplyStatus(nil, nil, svc)

	if svc["smp"] != "keep" {
		t.Fatalf("expected smp untouched, got %v", svc["smp"])
	}
	if _, ok := svc["xftp"]; ok {
		t.Fatal("expected no xftp key when pointer is nil")
	}
}

func TestServiceStatusNeverEmpty(t *testing.T) {
	// Without docker in CI this returns "Not running" — either way non-empty.
	smp, xftp := ServiceStatus()
	if smp == "" || xftp == "" {
		t.Fatalf("expected non-empty statuses, got smp=%q xftp=%q", smp, xftp)
	}
}
