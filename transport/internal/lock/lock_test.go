// Package lock provides mutex-based file locking for cross-process synchronization
package lock

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func newTestService(t *testing.T) *Service {
	t.Helper()
	dir, err := os.MkdirTemp("", "lock-test-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })
	return New(dir)
}


// TestNewCreatesDefaultCode handles the TestNewCreatesDefaultCode HTTP request.
func TestNewCreatesDefaultCode(t *testing.T) {
	s := newTestService(t)
	if s.state.Code == "" {
		t.Fatal("expected non-empty code")
	}
	if !isBcrypt(s.state.Code) {
		t.Fatal("expected bcrypt hash, got:", s.state.Code)
	}
}


// TestIsLockedInitial handles the TestIsLockedInitial HTTP request.
func TestIsLockedInitial(t *testing.T) {
	s := newTestService(t)
	if s.IsLocked() {
		t.Fatal("expected unlocked initially")
	}
}


// TestLock handles the TestLock HTTP request.
func TestLock(t *testing.T) {
	s := newTestService(t)
	s.Lock()
	if !s.IsLocked() {
		t.Fatal("expected locked after Lock()")
	}
}


// TestValidateUnlockCorrect handles the TestValidateUnlockCorrect HTTP request.
func TestValidateUnlockCorrect(t *testing.T) {
	s := newTestService(t)
	s.Lock()
	if !s.IsLocked() {
		t.Fatal("expected locked after Lock()")
	}
	if !s.ValidateUnlock("123456") {
		t.Fatal("expected unlock with default code")
	}
	if s.IsLocked() {
		t.Fatal("expected unlocked after ValidateUnlock")
	}
}


// TestValidateUnlockWrong handles the TestValidateUnlockWrong HTTP request.
func TestValidateUnlockWrong(t *testing.T) {
	s := newTestService(t)
	s.Lock()
	if s.ValidateUnlock("wrong") {
		t.Fatal("expected false for wrong code")
	}
	if !s.IsLocked() {
		t.Fatal("expected still locked after wrong code")
	}
}


// TestValidateUnlockRateLimit handles the TestValidateUnlockRateLimit HTTP request.
func TestValidateUnlockRateLimit(t *testing.T) {
	s := newTestService(t)
	s.Lock()
	for i := 0; i < 5; i++ {
		s.ValidateUnlock("wrong")
	}
	locked := s.IsLocked()
	if !locked {
		t.Fatal("expected still locked")
	}
}


// TestChangeCodeCorrect handles the TestChangeCodeCorrect HTTP request.
func TestChangeCodeCorrect(t *testing.T) {
	s := newTestService(t)
	if !s.ChangeCode("123456", "newcode") {
		t.Fatal("expected ChangeCode success")
	}
	if s.ValidateUnlock("123456") {
		t.Fatal("expected old code to fail")
	}
	if !s.ValidateUnlock("newcode") {
		t.Fatal("expected new code to work")
	}
}


// TestChangeCodeWrongCurrent handles the TestChangeCodeWrongCurrent HTTP request.
func TestChangeCodeWrongCurrent(t *testing.T) {
	s := newTestService(t)
	if s.ChangeCode("wrong", "newcode") {
		t.Fatal("expected ChangeCode failure with wrong current")
	}
}


// TestChangeCodeInvalidLength handles the TestChangeCodeInvalidLength HTTP request.
func TestChangeCodeInvalidLength(t *testing.T) {
	s := newTestService(t)
	if s.ChangeCode("123456", "ab") {
		t.Fatal("expected ChangeCode failure with short code")
	}
	if s.ChangeCode("123456", "") {
		t.Fatal("expected ChangeCode failure with empty code")
	}
	if !s.ChangeCode("123456", "abcd") {
		t.Fatal("expected ChangeCode success with 4-char code")
	}
}


// TestLoadFromDisk handles the TestLoadFromDisk HTTP request.
func TestLoadFromDisk(t *testing.T) {
	dir, err := os.MkdirTemp("", "lock-load-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })

	s1 := New(dir)
	s1.Lock()
	if !s1.IsLocked() {
		t.Fatal("expected locked")
	}

	s2 := New(dir)
	if !s2.IsLocked() {
		t.Fatal("expected locked after reload from disk")
	}
	if !s2.ValidateUnlock("123456") {
		t.Fatal("expected unlock after reload")
	}
}


// TestFilePermission handles the TestFilePermission HTTP request.
func TestFilePermission(t *testing.T) {
	s := newTestService(t)
	s.save()
	info, err := os.Stat(s.lockFile)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode()&0o077 != 0 {
		t.Fatalf("lock file has group/other perms: %v", info.Mode())
	}
}


// TestMultipleUnlocks handles the TestMultipleUnlocks HTTP request.
func TestMultipleUnlocks(t *testing.T) {
	s := newTestService(t)
	s.Lock()
	for i := 0; i < 3; i++ {
		s.Lock()
		if !s.ValidateUnlock("123456") {
			t.Fatalf("expected unlock attempt %d to work", i+1)
		}
	}
}


// TestConcurrentAccess handles the TestConcurrentAccess HTTP request.
func TestConcurrentAccess(t *testing.T) {
	s := newTestService(t)
	done := make(chan bool, 10)
	for i := 0; i < 10; i++ {
		go func() {
			s.Lock()
			s.ValidateUnlock("123456")
			done <- true
		}()
	}
	for i := 0; i < 10; i++ {
		<-done
	}
}


// TestSaveAndLoad handles the TestSaveAndLoad HTTP request.
func TestSaveAndLoad(t *testing.T) {
	dir, err := os.MkdirTemp("", "lock-saveload-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })
	s := New(dir)
	s.Lock()
	lockFile := filepath.Join(dir, "lock.json")
	b, err := os.ReadFile(lockFile)
	if err != nil {
		t.Fatal(err)
	}
	var state State
	if err := json.Unmarshal(b, &state); err != nil {
		t.Fatal(err)
	}
	if !state.IsLocked {
		t.Fatal("expected is_locked=true in saved file")
	}
	if !isBcrypt(state.Code) {
		t.Fatal("expected bcrypt code in saved file")
	}
}
