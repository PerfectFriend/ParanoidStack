#!/bin/bash
# bootstrap.sh - First run auto-setup for ParanoidX Client Node
# Detects hardware, generates configs, fetches subscriptions, tests, ranks

set -euo pipefail

DATA_DIR="/home/thomas/.local/share/ParanoidX"
SCRIPTS_DIR="/home/thomas/ParanoidX/scripts"
LOG_DIR="${DATA_DIR}/logs"

mkdir -p "$DATA_DIR" "$LOG_DIR"

RED="\033[0;31m"
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
BLUE="\033[0;34m"
NC="\033[0m"
log() { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARNING:${NC} $*"; }
error() { echo -e "${RED}[$(date '+%H:%M:%S')] ERROR:${NC} $*"; }
info() { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }

# Check if already bootstrapped
if [ -f "$DATA_DIR/.bootstrapped" ]; then
  info "Already bootstrapped. Run with --force to re-bootstrap."
  exit 0
fi

log "=== ParanoidX Client Node Bootstrap ==="
echo ""

# 1. Detect hardware and kernel features
log "1/8 Detecting hardware and kernel..."
CPU_FEATURES=$(lscpu | grep -E "Flags|features" | head -1)
if echo "$CPU_FEATURES" | grep -q "aes"; then info "AES-NI: YES"; else warn "AES-NI: NO"; fi
if echo "$CPU_FEATURES" | grep -q "sha"; then info "SHA-NI: YES"; else warn "SHA-NI: NO"; fi

KERNEL_VER=$(uname -r)
info "Kernel: $KERNEL_VER"

# Check kernel modules
for mod in wireguard xt_dscp xt_mark xt_set nf_tables; do
  if lsmod | grep -q "^$mod "; then info "Kernel module $mod: LOADED"; else warn "Kernel module $mod: NOT LOADED"; fi
done

# Check XDP/BPF
if ls /sys/class/bpf/ 2>/dev/null | grep -q .; then info "BPF filesystem: AVAILABLE"; else warn "BPF filesystem: NOT AVAILABLE"; fi

# 2. Install required binaries
log "2/8 Checking binaries..."
BINARIES=("wg" "wg-quick" "openvpn" "hysteria2" "tailscale" "tor" "xray" "curl" "jq" "sqlite3" "openssl")
for bin in "${BINARIES[@]}"; do
  if command -v "$bin" >/dev/null 2>&1; then
    info "$bin: OK"
  else
    warn "$bin: MISSING - attempting install"
    case "$bin" in
      xray)
        if [ ! -f "/home/thomas/bin/xray/xray" ]; then
          mkdir -p /home/thomas/bin/xray
          curl -sL https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-64.zip -o /tmp/xray.zip
          unzip -o /tmp/xray.zip -d /home/thomas/bin/xray/
          chmod +x /home/thomas/bin/xray/xray
        fi
        ;;
      hysteria2)
        curl -sL https://github.com/apernet/hysteria/releases/latest/download/hysteria-linux-amd64 -o /home/thomas/bin/hysteria2
        chmod +x /home/thomas/bin/hysteria2
        ;;
      *)
        apt-get update && apt-get install -y "$bin" 2>/dev/null || warn "Could not install $bin"
        ;;
    esac
  fi
done

# 3. Setup Tor (dual role)
log "3/8 Setting up Tor (Server + Client)..."
bash "$SCRIPTS_DIR/setup-tor-dual.sh"

# 4. Generate key pairs for client auth (if needed)
log "4/8 Generating client keys..."
# WireGuard keys for client
if [ ! -f "$DATA_DIR/wg-client.key" ]; then
  wg genkey > "$DATA_DIR/wg-client.key"
  wg pubkey < "$DATA_DIR/wg-client.key" > "$DATA_DIR/wg-client.pub"
  info "Generated WireGuard client keypair"
fi

# 5. Fetch and import subscriptions
log "5/8 Fetching subscriptions..."

# Default subscription URLs (can be customized)
# High-quality sources with high stars and good reputation
VPN1_SUB_URLS=(
  "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt"
  "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_base64_Sub.txt"
  "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub"
)

VPN2_SUB_URLS=(
  "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt"
  "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_base64_Sub.txt"
  "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub"
  "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub1.txt"
  "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub2.txt"
  "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub3.txt"
)

# Fetch VPN1 subscriptions
for url in "${VPN1_SUB_URLS[@]}"; do
  log "Fetching VPN1 subscription: $url"
  bash "$SCRIPTS_DIR/import-vpn1.sh" "$url" 2>&1 | grep -E "(Imported|Extracted)" || true
done

# Fetch VPN2 subscriptions
for url in "${VPN2_SUB_URLS[@]}"; do
  log "Fetching VPN2 subscription: $url"
  bash "$SCRIPTS_DIR/import-vpn2.sh" "$url" 2>&1 | grep -E "(Imported|Extracted)" || true
done

# 6. Test all imported configs
log "6/8 Testing imported configs..."

# Test VPN1 configs
VPN1_COUNT=0
for config in "$DATA_DIR/client-bridge/vpn1"/*.conf "$DATA_DIR/client-bridge/vpn1"/*.ovpn "$DATA_DIR/client-bridge/vpn1"/*.json; do
  if [ -f "$config" ]; then
    VPN1_COUNT=$((VPN1_COUNT + 1))
    proto=$(cat "${config}.proto" 2>/dev/null || echo "unknown")
    log "Testing VPN1: $(basename $config) ($proto)"
    # Quick test - just verify config syntax
    case "$proto" in
      wireguard) 
        if command -v wg-quick >/dev/null 2>&1; then
          wg-quick strip "$config" >/dev/null 2>&1 && log "  ✅ Syntax OK" || warn "  ❌ Syntax FAIL"
        else
          warn "  ⚠️ WireGuard tools not installed, skipping test"
        fi
        ;;
      openvpn) openvpn --config "$config" --test-crypto >/dev/null 2>&1 && log "  ✅ Syntax OK" || warn "  ❌ Syntax FAIL" ;;
      hysteria2) hysteria2 client -c "$config" --dry-run >/dev/null 2>&1 && log "  ✅ Syntax OK" || warn "  ❌ Syntax FAIL" ;;
    esac
  fi
done

# Test VPN2 configs
VPN2_COUNT=0
for config in "$DATA_DIR/client-bridge/vpn2"/*.json; do
  if [ -f "$config" ]; then
    VPN2_COUNT=$((VPN2_COUNT + 1))
    proto=$(cat "${config}.proto" 2>/dev/null || echo "unknown")
    log "Testing VPN2: $(basename $config) ($proto)"
    /home/thomas/bin/xray/xray run -c "$config" --test >/dev/null 2>&1 && log "  ✅ Syntax OK" || warn "  ❌ Syntax FAIL"
  fi
done

info "VPN1 configs: $VPN1_COUNT, VPN2 configs: $VPN2_COUNT"

# 7. Auto-select best configs (first working ones)
log "7/8 Selecting best configs..."

# Select first working VPN1
BEST_VPN1=""
BEST_VPN1_PROTO=""
for config in "$DATA_DIR/client-bridge/vpn1"/*; do
  if [ -f "$config" ] && [[ ! "$config" == *.proto ]]; then
    proto=$(cat "${config}.proto" 2>/dev/null)
    case "$proto" in
      wireguard)
        if wg-quick strip "$config" >/dev/null 2>&1; then
          BEST_VPN1="$config"
          BEST_VPN1_PROTO="$proto"
          break
        fi
        ;;
      openvpn)
        if openvpn --config "$config" --test-crypto >/dev/null 2>&1; then
          BEST_VPN1="$config"
          BEST_VPN1_PROTO="$proto"
          break
        fi
        ;;
      hysteria2)
        if hysteria2 client -c "$config" --dry-run >/dev/null 2>&1; then
          BEST_VPN1="$config"
          BEST_VPN1_PROTO="$proto"
          break
        fi
        ;;
    esac
  fi
done

# Select first working VPN2
BEST_VPN2=""
BEST_VPN2_PROTO=""
for config in "$DATA_DIR/client-bridge/vpn2"/*.json; do
  if [ -f "$config" ]; then
    proto=$(cat "${config}.proto" 2>/dev/null)
    if /home/thomas/bin/xray/xray run -c "$config" --test >/dev/null 2>&1; then
      BEST_VPN2="$config"
      BEST_VPN2_PROTO="$proto"
      break
    fi
  fi
done

if [ -n "$BEST_VPN1" ] && [ -n "$BEST_VPN2" ]; then
  log "Selected VPN1: $(basename $BEST_VPN1) ($BEST_VPN1_PROTO)"
  log "Selected VPN2: $(basename $BEST_VPN2) ($BEST_VPN2_PROTO)"
  
  # Save as active configs
  echo "$BEST_VPN1" > "$DATA_DIR/client-bridge/vpn1_config"
  echo "$BEST_VPN1_PROTO" > "$DATA_DIR/client-bridge/vpn1_proto"
  echo "$BEST_VPN2" > "$DATA_DIR/client-bridge/vpn2_config"
  echo "$BEST_VPN2_PROTO" > "$DATA_DIR/client-bridge/vpn2_proto"
else
  warn "Could not find working configs. Manual setup required."
fi

# 8. Create systemd services and start
log "8/8 Creating services..."

# Client bridge systemd service
cat > "/home/thomas/.config/systemd/user/paranoidx-client-bridge.service" <<'BRIDGESVC'
[Unit]
Description=ParanoidX Client Bridge (VPN1 -> VPN2 -> Tor)
After=network-online.target paranoidx-tor-server.service
Wants=network-online.target paranoidx-tor-server.service

[Service]
Type=oneshot
RemainAfterExit=yes
User=thomas
Group=thomas
Environment=HOME=/home/thomas
Environment=USER=thomas

ExecStart=/home/thomas/ParanoidX/scripts/client-bridge.sh start
ExecStop=/home/thomas/ParanoidX/scripts/client-bridge.sh stop
TimeoutStartSec=60
TimeoutStopSec=30

[Install]
WantedBy=default.target
BRIDGESVC

systemctl --user daemon-reload
systemctl --user enable paranoidx-client-bridge.service

# Start if configs selected
if [ -n "$BEST_VPN1" ] && [ -n "$BEST_VPN2" ]; then
  log "Starting client bridge..."
  systemctl --user start paranoidx-client-bridge.service
  sleep 5
  
  # Test chain
  log "Testing chain..."
  bash "$SCRIPTS_DIR/client-bridge.sh" test
fi

# Mark bootstrapped
touch "$DATA_DIR/.bootstrapped"
echo "$(date +%s)" > "$DATA_DIR/.bootstrapped"

log "=== Bootstrap Complete ==="
echo ""
echo "Services:"
echo "  Tor Server (hidden services): systemctl --user status paranoidx-tor-server.service"
echo "  Client Bridge (VPN1->VPN2->Tor): systemctl --user status paranoidx-client-bridge.service"
echo ""
echo "Manual commands:"
echo "  Add VPN1: $SCRIPTS_DIR/import-vpn1.sh <config> <proto>"
echo "  Add VPN2: $SCRIPTS_DIR/import-vpn2.sh <config> <proto>"
echo "  Start bridge: $SCRIPTS_DIR/client-bridge.sh start"
echo "  Test chain: $SCRIPTS_DIR/client-bridge.sh test"
echo "  Status: $SCRIPTS_DIR/client-bridge.sh status"
