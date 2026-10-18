# AHMADI-NET Cloudflare Worker

This Worker implements a real RouterOS Hotspot customer-account API. It is not connected until the Cloudflare VPC route, tunnel connector, and Worker secrets are configured.

## Routes

- `GET /health` — confirms the Worker is deployed only; it does **not** claim the router is reachable.
- `POST /api/login` — body `{"username":"...","password":"..."}`; verifies a local Hotspot user's password against RouterOS and returns a short-lived signed session plus that user's account data.
- `GET /api/account` — requires `Authorization: Bearer <session>`; returns only the account named in the signed session.

No customer password is stored or returned by the Worker. Session tokens expire after one hour.

## Required Cloudflare configuration

1. Create a Cloudflare Tunnel connector on a device that can route to `172.16.1.1`. A tunnel that exists only in the dashboard is not running. The connector must remain online.
2. In Workers VPC, configure a VPC Network route that reaches the connector/tunnel and the private subnet containing `172.16.1.1`. Bind this Worker to that VPC Network. The included `wrangler.jsonc` uses `cf1:network`; adjust it to the actual network ID if your Cloudflare account requires a specific network.
3. Ensure the connector device can reach `172.16.1.1:8728` over the LAN/VPN. A Worker cannot reach this RFC1918 address without the private route.
4. Set the following Worker secrets from a trusted terminal:
   - `ROUTER_API_USER`: dedicated MikroTik API account.
   - `ROUTER_API_PASSWORD`: that account's password.
   - `SESSION_SECRET`: at least 32 random characters; use a unique random secret.
5. Deploy with Wrangler from this folder:
   ```sh
   npm install --save-dev wrangler
   npx wrangler secret put ROUTER_API_USER
   npx wrangler secret put ROUTER_API_PASSWORD
   npx wrangler secret put SESSION_SECRET
   npx wrangler deploy
   ```
6. Confirm the deployed Worker name/URL matches `https://ahmadinet.rahmat-ahmadi1047.workers.dev`. Do not paste secrets into chat, source code, or GitHub.

## MikroTik permissions and security

The current implementation authenticates local Hotspot users by reading the password field for the requested username. On RouterOS this may require the API service account to have the `sensitive` policy in addition to `read,api`. This is a security trade-off: such an account may be able to read sensitive user fields. Use a dedicated account, restrict API service/firewall access to the private connector path, and never expose TCP 8728 publicly. If you do not accept this permission, do not deploy this password-check approach; use a separate RADIUS authentication integration instead.

Create a dedicated group/user in WinBox and give only the required policies. Limit `/ip service api` and firewall access to the connector's private source address/subnet where possible. Never use the router's full administrator credentials in the Worker.

## Availability limitation

A Cloudflare Tunnel connector must run on an always-on device that can reach the router. If your laptop is the only device in the router LAN and is switched off, the route will stop working. For an always-online setup without your laptop, use a small always-on device on the LAN or a VPS connected to the MikroTik by a site-to-site WireGuard VPN; a VPS alone cannot reach `172.16.1.1` without that private route.

## Rate limiting

Before publishing to customers, configure a Cloudflare rate-limiting rule for `POST /api/login` (for example, a conservative per-IP request limit) and review logs. The Worker deliberately does not implement a fake in-memory rate limiter because it would not be reliable across isolates. Use HTTPS only, and rotate any token/password previously pasted into chat.

## Data limits

RouterOS Hotspot local users provide byte counters and optionally `limit-bytes-total`. The Worker shows remaining data only when a total byte limit is set. Expiry dates are not fabricated; they are not shown unless a real expiry field is added to the source system.
