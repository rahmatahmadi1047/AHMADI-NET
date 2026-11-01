# AHMADI-NET Cloudflare Worker

This Worker provides the customer account API and a restricted seller panel for the second MikroTik RB951 router.

## Routes

- GET /health — confirms the Worker is deployed; it does not prove router connectivity.
- POST /api/login and GET /api/account — customer account status.
- GET /admin — Persian RTL seller panel for Hotspot and User Manager users.
- GET /api/admin/profiles?type=hotspot|usermanager — lists existing profiles only.
- GET /api/admin/users?type=hotspot|usermanager — lists accounts.
- POST /api/admin/users — creates a user in the selected system and assigns an existing profile.
- DELETE /api/admin/users?type=hotspot|usermanager&username=... — deletes only the selected user. For Hotspot, active sessions for that user are disconnected first. For User Manager, profile assignments belonging to that user are removed first.

The seller panel does not expose commands for changing router settings, profiles, limitations, firewall, DNS, wireless, IP addressing, or router administrator accounts. It refuses duplicate usernames and never edits an existing account.

## RB951 and Cloudflare configuration

The intended router target is 192.168.60.1:8728, not the main router at 172.16.1.1.

1. Confirm the Cloudflare Tunnel connector is running on a device that can route to the RB951 subnet.
2. Confirm the Cloudflare VPC route includes 192.168.60.0/24 and the connector can reach 192.168.60.1:8728.
3. In the Worker settings, set ROUTER_HOST=192.168.60.1 and ROUTER_PORT=8728. A dashboard variable can override the value in wrangler.jsonc; verify the deployed Worker variable too.
4. Keep the existing PRIVATE_NETWORK VPC binding and ensure it points at the connected tunnel network.
5. Create or retain the Worker secrets ROUTER_API_USER and ROUTER_API_PASSWORD for a dedicated API-only MikroTik account.
6. Create the Worker secret ADMIN_TOKEN with at least 32 random characters. Do not place this token in source code, GitHub, screenshots, or chat. The same token is entered at runtime in the website or Android app; it is not hard-coded into the APK.
7. Deploy the Worker from this folder after checking the Worker name and VPC binding against the Cloudflare dashboard.

The existing Worker URL is expected to be https://ahmadinet.rahmat-ahmadi1047.workers.dev; open /admin after deployment.

## MikroTik API account security

The Worker uses a fixed allow-list of RouterOS commands for listing profiles/users, adding users, deleting users, and removing a user's own Hotspot active sessions or User Manager profile assignments. There is no endpoint that accepts arbitrary RouterOS commands.

The RouterOS API account still needs read/write/API permissions for these operations. RouterOS does not provide a built-in per-menu write policy for ordinary user groups, so this account could have broad write capability if its credentials were stolen and the API were reachable directly. Therefore:
- use a dedicated API-only account, not the router's full administrator account;
- do not give the seller the RouterOS API username/password;
- restrict /ip service api and firewall access to the trusted private tunnel/connector source wherever possible;
- never expose TCP 8728 publicly;
- protect the Worker with the long random ADMIN_TOKEN and Cloudflare rate limiting / Access controls.

The seller token can perform only the allow-listed operations implemented by this Worker. It cannot directly log in to WinBox.

## User Manager behavior

User Manager users are created under /user-manager/user and assigned one of the already-existing profiles through /user-manager/user-profile. The panel never creates, edits, or deletes profiles or limitations. The existing profile's starts-when=first-auth, validity, and limitations remain responsible for activation and expiry.

Before issuing paid codes, test with one new test username and verify it appears in both the expected User Manager menu and the Hotspot authentication flow. Do not use an existing customer name for testing.

## Android app

The Android seller app is the admin-app Gradle module. It uses the same Worker endpoints as the website, asks the seller to enter ADMIN_TOKEN at runtime, and does not persist the token. GitHub Actions builds admin-app-debug.apk alongside the existing apps.

## Important operational notes

- A healthy Worker /health response does not prove the VPC route or RouterOS API works.
- The Cloudflare connector must remain online. If it is on a laptop that is switched off, remote management will stop.
- This feature does not change any existing Hotspot users, User Manager profiles, limitations, or router settings.
