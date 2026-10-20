# AHMADI-NET

This branch adds a separate **AHMADI VPN** Android app module at `:vpn-app`.

## AHMADI VPN status

- Embeds the official WireGuard Android tunnel engine (`com.wireguard.android:tunnel`).
- Imports a WireGuard `.conf` file or pasted WireGuard configuration.
- Requests Android VPN permission and provides in-app connect/disconnect controls.
- OpenVPN `.ovpn` and VLESS/Xray links are **not yet supported by an embedded engine** in this version.

Build debug APKs with the GitHub Actions workflow `Android APK`; it builds the existing AHMADI-NET apps as well as AHMADI VPN. A successful build does not by itself prove connection to a live VPN server; test with a valid WireGuard configuration and reachable server.
