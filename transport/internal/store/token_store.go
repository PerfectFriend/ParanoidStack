package store

import (
	"fmt"
	"time"
)

type Token struct {
	Symbol          string    `json:"symbol"`
	Name            string    `json:"name"`
	Decimals        int       `json:"decimals"`
	Chain           string    `json:"chain"`
	ContractAddress string    `json:"contract_address,omitempty"`
	LogoURL         string    `json:"logo_url,omitempty"`
	IsCustom        bool      `json:"is_custom"`
	CreatedAt       time.Time `json:"created_at"`
}

type TokenBalance struct {
	Symbol    string `json:"symbol"`
	Name      string `json:"name"`
	Balance   string `json:"balance"`
	UpdatedAt string `json:"updated_at,omitempty"`
}

type TokenStore struct {
	db *DB
}

func NewTokenStore(db *DB) *TokenStore {
	ts := &TokenStore{db: db}
	ts.migrate()
	ts.seedDefaults()
	return ts
}

func (ts *TokenStore) migrate() {
	_, _ = ts.db.Exec(`CREATE TABLE IF NOT EXISTS tokens (
		symbol TEXT PRIMARY KEY,
		name TEXT NOT NULL,
		decimals INTEGER NOT NULL DEFAULT 18,
		chain TEXT NOT NULL DEFAULT 'custom',
		contract_address TEXT NOT NULL DEFAULT '',
		logo_url TEXT NOT NULL DEFAULT '',
		is_custom INTEGER NOT NULL DEFAULT 0,
		created_at TEXT NOT NULL
	)`)
	_, _ = ts.db.Exec(`CREATE TABLE IF NOT EXISTS token_balances (
		pubkey TEXT NOT NULL,
		symbol TEXT NOT NULL,
		balance TEXT NOT NULL DEFAULT '0',
		updated_at TEXT NOT NULL,
		PRIMARY KEY (pubkey, symbol)
	)`)
}

func (ts *TokenStore) seedDefaults() {
	top10 := []Token{
		{Symbol: "NG", Name: "Node Gold", Decimals: 0, Chain: "isle", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "BTC", Name: "Bitcoin", Decimals: 8, Chain: "bitcoin", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "ETH", Name: "Ethereum", Decimals: 18, Chain: "ethereum", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "USDT", Name: "Tether USD", Decimals: 6, Chain: "ethereum", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "SOL", Name: "Solana", Decimals: 9, Chain: "solana", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "XRP", Name: "XRP", Decimals: 6, Chain: "ripple", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "ADA", Name: "Cardano", Decimals: 6, Chain: "cardano", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "AVAX", Name: "Avalanche", Decimals: 18, Chain: "avalanche", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "DOT", Name: "Polkadot", Decimals: 10, Chain: "polkadot", IsCustom: false, CreatedAt: time.Now()},
		{Symbol: "LINK", Name: "Chainlink", Decimals: 18, Chain: "ethereum", IsCustom: false, CreatedAt: time.Now()},
	}
	for _, t := range top10 {
		_, _ = ts.db.Exec(`INSERT OR IGNORE INTO tokens (symbol, name, decimals, chain, is_custom, created_at) VALUES (?, ?, ?, ?, ?, ?)`,
			t.Symbol, t.Name, t.Decimals, t.Chain, boolToInt(t.IsCustom), formatTime(t.CreatedAt))
	}
}

func (ts *TokenStore) ListTokens() ([]Token, error) {
	return ts.queryTokens(`SELECT symbol, name, decimals, chain, contract_address, logo_url, is_custom, created_at FROM tokens ORDER BY is_custom ASC, symbol ASC`)
}

func (ts *TokenStore) GetBalances(pubkey string) ([]TokenBalance, error) {
	rows, err := ts.db.Query(`SELECT t.symbol, t.name, COALESCE(b.balance, '0'), COALESCE(b.updated_at, '') FROM tokens t LEFT JOIN token_balances b ON t.symbol = b.symbol AND b.pubkey = ? ORDER BY t.is_custom ASC, t.symbol ASC`, pubkey)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var balances []TokenBalance
	for rows.Next() {
		var tb TokenBalance
		if err := rows.Scan(&tb.Symbol, &tb.Name, &tb.Balance, &tb.UpdatedAt); err != nil {
			return nil, err
		}
		balances = append(balances, tb)
	}
	return balances, rows.Err()
}

func (ts *TokenStore) AddToken(t Token) error {
	existing, _ := ts.db.Query(`SELECT symbol FROM tokens WHERE symbol = ?`, t.Symbol)
	if existing.Next() {
		existing.Close()
		return fmt.Errorf("token %s already exists", t.Symbol)
	}
	existing.Close()
	_, err := ts.db.Exec(`INSERT INTO tokens (symbol, name, decimals, chain, contract_address, logo_url, is_custom, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		t.Symbol, t.Name, t.Decimals, t.Chain, t.ContractAddress, t.LogoURL, boolToInt(t.IsCustom), formatTime(time.Now()))
	return err
}

func (ts *TokenStore) RemoveToken(symbol string) error {
	_, err := ts.db.Exec(`DELETE FROM tokens WHERE symbol = ? AND is_custom = 1`, symbol)
	return err
}

func (ts *TokenStore) SetBalance(pubkey, symbol, balance string) error {
	_, err := ts.db.Exec(`INSERT OR REPLACE INTO token_balances (pubkey, symbol, balance, updated_at) VALUES (?, ?, ?, ?)`,
		pubkey, symbol, balance, formatTime(time.Now()))
	return err
}

func (ts *TokenStore) queryTokens(query string, args ...any) ([]Token, error) {
	rows, err := ts.db.Query(query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var tokens []Token
	for rows.Next() {
		var t Token
		var isCustom int
		var createdAt string
		if err := rows.Scan(&t.Symbol, &t.Name, &t.Decimals, &t.Chain, &t.ContractAddress, &t.LogoURL, &isCustom, &createdAt); err != nil {
			return nil, err
		}
		t.IsCustom = intToBool(isCustom)
		t.CreatedAt, _ = parseTime(createdAt)
		tokens = append(tokens, t)
	}
	return tokens, rows.Err()
}

