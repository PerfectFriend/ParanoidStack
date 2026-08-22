# Transport Layer Docker Templates
#
# This directory contains template configurations for the ParanoidStack transport layer:
# - VPN1 (OpenVPN/WireGuard/Hysteria2) - First hop
# - VPN2 (V2Ray/Xray with VLESS/REALITY, Trojan) - Second hop
# - Tor (Embedded daemon with 5 hidden services) - Final hop
# - SimpleX (SMP + XFTP over Tor onion) - Secure messaging
# - Coturn (STUN/TURN for WebRTC) - NAT traversal
#
# ## Quick Start
#
# 1. Copy templates to actual config files:
#    ```bash
#    cp v2ray/config.json.template v2ray/config.json
#    cp v2ray/.env.template v2ray/.env
#    cp coturn/.env.template coturn/.env
#    cp docker-compose.yml.template docker-compose.yml
#    ```
#
# 2. Fill in your secrets in `.env` files (NEVER commit these!)
#
# 3. Start the stack:
#    ```bash
#    docker-compose up -d
#    ```
#
# ## Architecture
#
# ```
# Client → [VPN1] → [VPN2 (V2Ray)] → [Tor SOCKS5] → [SimpleX Onion Services]
#                    ↓
#              [Coturn STUN/TURN] (for WebRTC)
# ```
#
# ## Services
#
# - **v2ray**: Xray core with VLESS-REALITY inbound + Trojan outbound chain
# - **tor**: Embedded Tor daemon with 5 hidden services (SMP, XFTP, Dashboard, ICE, etc.)
# - **smp-server**: SimpleX Message Protocol server
# - **xftp-server**: SimpleX File Transfer Protocol server
# - **coturn**: STUN/TURN server for WebRTC NAT traversal
#
# ## Ports
#
# - 10808: V2Ray SOCKS5 inbound
# - 10809: V2Ray HTTP inbound
# - 10810: V2Ray VLESS-REALITY inbound
# - 9050: Tor SOCKS5 (internal only)
# - 5223: SMP server (internal only)
# - 443: XFTP server (internal only)
# - 3478/5349: Coturn STUN/TURN
#
# ## Environment Variables
#
# See `.env.template` files for required variables. All secrets must be provided
# via environment variables or Docker secrets - never hardcoded in config files.