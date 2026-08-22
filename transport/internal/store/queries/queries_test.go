// Package queries provides SQLite query helpers
package queries

import (
	"strings"
	"testing"
)


// TestStationQueries handles the TestStationQueries HTTP request.
func TestStationQueries(t *testing.T) {
	if InsertStation == "" {
		t.Fatal("InsertStation query should not be empty")
	}
	if !strings.Contains(InsertStation, "INSERT") {
		t.Fatal("InsertStation should be INSERT query")
	}
	if SelectAllStations == "" {
		t.Fatal("SelectAllStations query should not be empty")
	}
	if SelectStation == "" {
		t.Fatal("SelectStation query should not be empty")
	}
	if DeleteStation == "" {
		t.Fatal("DeleteStation query should not be empty")
	}
}


// TestAnnouncementQueries handles the TestAnnouncementQueries HTTP request.
func TestAnnouncementQueries(t *testing.T) {
	if InsertAnnouncement == "" {
		t.Fatal("InsertAnnouncement query should not be empty")
	}
	if !strings.Contains(InsertAnnouncement, "INSERT") {
		t.Fatal("InsertAnnouncement should be INSERT query")
	}
	if SelectPendingAnnouncements == "" {
		t.Fatal("SelectPendingAnnouncements should not be empty")
	}
	if !strings.Contains(SelectPendingAnnouncements, "played_at IS NULL") {
		t.Fatal("SelectPendingAnnouncements should filter unplayed")
	}
	if MarkAnnouncementPlayed == "" {
		t.Fatal("MarkAnnouncementPlayed should not be empty")
	}
}
