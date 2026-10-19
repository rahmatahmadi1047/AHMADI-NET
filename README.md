# AHMADI-NET

This branch adds a separate **AHMADI VPN** Android app module at `:vpn-app`.

Build locally with:
```sh
gradle :vpn-app:assembleDebug
```

The initial app supports config selection and handing off to a compatible installed VPN client. It does not yet embed its own OpenVPN, WireGuard, or Xray/V2Ray engines.
