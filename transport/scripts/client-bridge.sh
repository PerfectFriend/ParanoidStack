#!/bin/bash

RED="\033[0;31m"
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
BLUE="\033[0;34m"
NC="\033[0m"
# client-bridge.sh — ParanoidX Client Bridge
# Chain: VPN1 (WireGuard/OpenVPN/Hysteria2) -> VPN2 (Xray VLESS/VMess/Trojan/SS) -> Tor
# Usage: ./client-bridge.sh [start|stop|status|add-vpn1|add-vpn2|test]

set -euo pipefail

DATA_DIR="/home/thomas/.local/share/ParanoidX"
CONFIG_DIR="${DATA_DIR}/client-bridge"
VPN1_DIR="${CONFIG_DIR}/vpn1"
VPN2_DIR="${CONFIG_DIR}/vpn2"
LOG_DIR="${DATA_DIR}/logs"
PID_DIR="${DATA_DIR}/pids"

mkdir -p "$VPN1_DIR" "$VPN2_DIR" "$LOG_DIR" "$PID_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARNING:${NC} $*"; }
error() { echo -e "${RED}[$(date '+%H:%M:%S')] ERROR:${NC} $*"; }
info() { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }

# === VPN1: Outer layer (WireGuard/OpenVPN/Hysteria2) ===
# Connects to first-hop VPN server

start_vpn1() {
  local config_file="$1"
  local proto="$2"
  
  if [ ! -f "$config_file" ]; then
    error "VPN1 config not found: $config_file"
    return 1
  fi
  
  case "$proto" in
    wireguard)
      if ! command -v wg-quick >/dev/null 2>&1; then
        error "WireGuard tools not installed (wg-quick command not found). Install with: sudo apt-get install wireguard-tools"
        return 1
      fi
      log "Starting WireGuard VPN1..."
      wg-quick up "$config_file" 2>&1 | tee -a "$LOG_DIR/vpn1-wg.log"
      ;;
    openvpn)
      log "Starting OpenVPN VPN1..."
      openvpn --config "$config_file" --daemon --log "$LOG_DIR/vpn1-ovpn.log" --writepid "$PID_DIR/vpn1.pid"
      ;;
    hysteria2)
      log "Starting Hysteria2 VPN1..."
      hysteria2 client -c "$config_file" 2>&1 | tee -a "$LOG_DIR/vpn1-hy2.log" &
      echo $! > "$PID_DIR/vpn1-hy2.pid"
      ;;
    *)
      error "Unknown VPN1 protocol: $proto"
      return 1
      ;;
  esac
  
  # Wait for interface
  sleep 3
  
  # Get the VPN interface IP
  local vpn_ip=$(ip route show | grep -E "(tun|wg)" | head -1 | awk '{print $9}' | head -1)
  if [ -n "$vpn_ip" ]; then
    echo "$vpn_ip" > "$CONFIG_DIR/vpn1_ip"
    log "VPN1 connected via interface with IP: $vpn_ip"
  else
    warn "VPN1 interface IP not detected"
  fi
  
  echo "$proto" > "$CONFIG_DIR/vpn1_proto"
  echo "$config_file" > "$CONFIG_DIR/vpn1_config"
}

# === VPN2: Inner layer (Xray VLESS/VMess/Trojan/SS) ===
# Runs over VPN1, connects to second-hop Xray server

start_vpn2() {
  local config_file="$1"
  local proto="$2"
  
  if [ ! -f "$config_file" ]; then
    error "VPN2 config not found: $config_file"
    return 1
  fi
  
  # VPN2 runs over VPN1 interface - bind to VPN1 IP if available
  local vpn1_ip=$(cat "$CONFIG_DIR/vpn1_ip" 2>/dev/null || echo "0.0.0.0")
  
  case "$proto" in
    vless|vmess|trojan|shadowsocks)
      log "Starting Xray VPN2 ($proto)..."
      # Xray client config with outbound to VPN2 server
      # Bind to VPN1 interface if available
      if [ "$vpn1_ip" != "0.0.0.0" ]; then
        # Modify config to bind to VPN1 interface
        local temp_config=$(mktemp)
        sed "s/\"bindAddress\": \"0\.0\.0\.0\"/\"bindAddress\": \"$vpn1_ip\"/g" "$config_file" > "$temp_config"
        XRAY_BIN="/home/thomas/bin/xray/xray"
        $XRAY_BIN run -c "$temp_config" 2>&1 | tee -a "$LOG_DIR/vpn2-xray.log" &
        echo $! > "$PID_DIR/vpn2.pid"
        rm -f "$temp_config"
      else
        XRAY_BIN="/home/thomas/bin/xray/xray"
        $XRAY_BIN run -c "$config_file" 2>&1 | tee -a "$LOG_DIR/vpn2-xray.log" &
        echo $! > "$PID_DIR/vpn2.pid"
      fi
      ;;
    *)
      error "Unknown VPN2 protocol: $proto"
      return 1
      ;;
  esac
  
  sleep 2
  
  # Get SOCKS proxy port from VPN2 (usually 10808)
  local socks_port=$(grep -oP '"port": \K[0-9]+' "$config_file" | head -1)
  if [ -z "$socks_port" ]; then
    socks_port=10808
  fi
  echo "$socks_port" > "$CONFIG_DIR/vpn2_socks_port"
  echo "$proto" > "$CONFIG_DIR/vpn2_proto"
  echo "$config_file" > "$CONFIG_DIR/vpn2_config"
  
  log "VPN2 ($proto) started, SOCKS proxy on 127.0.0.1:$socks_port"
}

# === Tor: Final layer over VPN2 SOCKS ===
start_tor() {
  local socks_port=$(cat "$CONFIG_DIR/vpn2_socks_port" 2>/dev/null || echo "10808")
  
  log "Starting Tor over VPN2 SOCKS (port $socks_port)..."
  
  # Generate torrc with VPN2 SOCKS as upstream proxy
  cat > "$CONFIG_DIR/torrc" <<TORRC
SocksPort 127.0.0.1:9050
ControlPort 127.0.0.1:9051
CookieAuthentication 1
DataDirectory $DATA_DIR/tor-client

# Use VPN2 SOCKS as upstream proxy
Socks5Proxy 127.0.0.1:$socks_port

# Only allow connections through VPN2
EnforceDistinctSubnets 0
UseEntryGuards 1
EntryGuards 3

# Circuit settings
NewCircuitPeriod 300
MaxClientCircuitsPending 100

# Logging
Log notice file $LOG_DIR/tor-client.log
SafeLogging 1
TORRC

  # Start tor
  tor -f "$CONFIG_DIR/torrc" 2>&1 | tee -a "$LOG_DIR/tor-client.log" &
  echo $! > "$PID_DIR/tor.pid"
  
  sleep 5
  
  # Verify Tor is working
  if curl -s --socks5 127.0.0.1:9050 --max-time 10 https://check.torproject.org/api/ip 2>/dev/null | grep -q "IsTor.*true"; then
    log "✅ Tor working! Exit IP: $(curl -s --socks5 127.0.0.1:9050 --max-time 10 https://check.torproject.org/api/ip 2>/dev/null)"
  else
    warn "Tor verification failed, but process started"
  fi
}

# === Stop all ===
stop_all() {
  log "Stopping all bridge components..."
  
  # Kill Tor
  if [ -f "$PID_DIR/tor.pid" ]; then
    kill $(cat "$PID_DIR/tor.pid") 2>/dev/null || true
    rm -f "$PID_DIR/tor.pid"
  fi
  
  # Kill VPN2
  if [ -f "$PID_DIR/vpn2.pid" ]; then
    kill $(cat "$PID_DIR/vpn2.pid") 2>/dev/null || true
    rm -f "$PID_DIR/vpn2.pid"
  fi
  
  # Kill VPN1
  if [ -f "$PID_DIR/vpn1.pid" ]; then
    kill $(cat "$PID_DIR/vpn1.pid") 2>/dev/null || true
    rm -f "$PID_DIR/vpn1.pid"
  fi
  
  if [ -f "$PID_DIR/vpn1-hy2.pid" ]; then
    kill $(cat "$PID_DIR/vpn1-hy2.pid") 2>/dev/null || true
    rm -f "$PID_DIR/vpn1-hy2.pid"
  fi
  
  # wg-quick down
  if [ -f "$CONFIG_DIR/vpn1_config" ]; then
    wg-quick down "$(cat $CONFIG_DIR/vpn1_config)" 2>/dev/null || true
  fi
  
  # openvpn
  pkill -f "openvpn.*vpn1" 2>/dev/null || true
  
  log "All components stopped"
}

# === Status ===
status() {
  echo "=== ParanoidX Client Bridge Status ==="
  echo ""
  
  if [ -f "$CONFIG_DIR/vpn1_proto" ]; then
    local vpn1_proto=$(cat "$CONFIG_DIR/vpn1_proto")
    local vpn1_config=$(cat "$CONFIG_DIR/vpn1_config")
    echo "VPN1 ($vpn1_proto): $vpn1_config"
    if [ -f "$PID_DIR/vpn1.pid" ] || [ -f "$PID_DIR/vpn1-hy2.pid" ]; then
      echo "  Status: RUNNING"
    else
      echo "  Status: STOPPED"
    fi
  else
    echo "VPN1: NOT CONFIGURED"
  fi
  
  echo ""
  
  if [ -f "$CONFIG_DIR/vpn2_proto" ]; then
    local vpn2_proto=$(cat "$CONFIG_DIR/vpn2_proto")
    local vpn2_config=$(cat "$CONFIG_DIR/vpn2_config")
    local socks_port=$(cat "$CONFIG_DIR/vpn2_socks_port" 2>/dev/null || echo "10808")
    echo "VPN2 ($vpn2_proto): $vpn2_config"
    echo "  SOCKS: 127.0.0.1:$socks_port"
    if [ -f "$PID_DIR/vpn2.pid" ]; then
      echo "  Status: RUNNING"
    else
      echo "  Status: STOPPED"
    fi
  else
    echo "VPN2: NOT CONFIGURED"
  fi
  
  echo ""
  echo "Tor: SOCKS 127.0.0.1:9050"
  if [ -f "$PID_DIR/tor.pid" ]; then
    echo "  Status: RUNNING"
  else
    echo "  Status: STOPPED"
  fi
}

# === Add VPN1 config ===
add_vpn1() {
  local source_file="$1"
  local proto="$2"
  
  if [ ! -f "$source_file" ]; then
    error "Source config not found: $source_file"
    return 1
  fi
  
  local dest="$VPN1_DIR/$(basename $source_file)"
  cp "$source_file" "$dest"
  echo "$proto" > "$VPN1_DIR/$(basename $source_file).proto"
  log "VPN1 config added: $dest (proto: $proto)"
}

# === Add VPN2 config ===
add_vpn2() {
  local source_file="$1"
  local proto="$2"
  
  if [ ! -f "$source_file" ]; then
    error "Source config not found: $source_file"
    return 1
  fi
  
  local dest="$VPN2_DIR/$(basename $source_file)"
  cp "$source_file" "$dest"
  echo "$proto" > "$VPN2_DIR/$(basename $source_file).proto"
  log "VPN2 config added: $dest (proto: $proto)"
}

# === Test chain ===
test_chain() {
  log "Testing full chain: VPN1 -> VPN2 -> Tor"
  
  local test_url="https://check.torproject.org/api/ip"
  
  # Test Tor
  if curl -s --socks5 127.0.0.1:9050 --max-time 15 "$test_url" | grep -q "IsTor.*true"; then
    log "✅ Tor working"
    curl -s --socks5 127.0.0.1:9050 --max-time 15 "$test_url" | jq -r '.IP'
  else
    error "❌ Tor not working"
  fi
  
  # Test VPN2 SOCKS
  local socks_port=$(cat "$CONFIG_DIR/vpn2_socks_port" 2>/dev/null || echo "10808")
  if curl -s --socks5 127.0.0.1:$socks_port --max-time 15 https://httpbin.org/ip 2>/dev/null | jq -r '.origin' 2>/dev/null; then
    log "✅ VPN2 SOCKS working"
  else
    warn "VPN2 SOCKS test failed"
  fi
  
  # Test direct (should go through VPN1)
  if curl -s --max-time 10 https://httpbin.org/ip 2>/dev/null | jq -r '.origin' 2>/dev/null; then
    log "✅ VPN1 working"
  else
    warn "VPN1 test failed"
  fi
}

# === Main ===
case "${1:-}" in
  start)
    # Auto-start from saved configs
    if [ -f "$CONFIG_DIR/vpn1_config" ] && [ -f "$CONFIG_DIR/vpn1_proto" ]; then
      start_vpn1 "$(cat $CONFIG_DIR/vpn1_config)" "$(cat $CONFIG_DIR/vpn1_proto)"
    else
      error "VPN1 not configured. Use: add-vpn1 <config> <proto>"
      exit 1
    fi
    
    if [ -f "$CONFIG_DIR/vpn2_config" ] && [ -f "$CONFIG_DIR/vpn2_proto" ]; then
      start_vpn2 "$(cat $CONFIG_DIR/vpn2_config)" "$(cat $CONFIG_DIR/vpn2_proto)"
    else
      error "VPN2 not configured. Use: add-vpn2 <config> <proto>"
      exit 1
    fi
    
    start_tor
    status
    ;;
  stop)
    stop_all
    ;;
  restart)
    stop_all
    sleep 2
    $0 start
    ;;
  status)
    status
    ;;
  add-vpn1)
    add_vpn1 "$2" "$3"
    ;;
  add-vpn2)
    add_vpn2 "$2" "$3"
    ;;
  test)
    test_chain
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|add-vpn1|add-vpn2|test}"
    echo ""
    echo "Commands:"
    echo "  add-vpn1 <config_file> <wireguard|openvpn|hysteria2>  - Add VPN1 (outer) config"
    echo "  add-vpn2 <config_file> <vless|vmess|trojan|shadowsocks> - Add VPN2 (inner) config"
    echo "  start                                                  - Start full chain"
    echo "  stop                                                   - Stop all"
    echo "  restart                                                - Restart all"
    echo "  status                                                 - Show status"
    echo "  test                                                   - Test full chain"
    exit 1
    ;;
esac
