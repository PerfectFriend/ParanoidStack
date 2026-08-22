// Package lock provides a PIN-based locking service for the ParanoidX vault.
// It uses bcrypt-hashed PIN codes persisted to a JSON file, with configurable unlock
// attempt limiting (5 failures triggers a 5-second delay). Key exported types include
// Service with Lock, IsLocked, ValidateUnlock, and ChangeCode methods. Default PIN is
// "123456" and is automatically bcrypt-hashed on first use.
package lock
