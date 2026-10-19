# CosmoX

CosmoX is a new Android VPN client built around the official XTLS/libXray wrapper for Xray-core.

## Current implementation
- Android VPNService and Xray's native TUN inbound integration.
- VLESS/Reality and other share-link formats supported by the bundled libXray parser.
- Import a single share link, newline-separated share links, or a subscription URL.
- Native AAR and debug APK built in GitHub Actions.

## Build
Push to `main` or run **Build CosmoX Android APK** manually in GitHub Actions. The workflow checks out XTLS/libXray, builds its Android AAR, and then builds the APK. Download the `CosmoX-debug-apk` artifact from the workflow run.

## Notes
- This project is under active development; test with a server configuration you control.
- A successful APK build does not prove connectivity to a real server. Verify installation, VPN permission, DNS, and traffic on a test device.
- XTLS/libXray is an external dependency. Review its license and upstream API changes when updating it.
