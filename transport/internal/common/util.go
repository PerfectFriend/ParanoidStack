package common

import (
	"encoding/json"
	"net/http"
	"time"
)

var StartTime = time.Now()

func WriteJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}
