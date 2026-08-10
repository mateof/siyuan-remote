# Security

## Reporting a vulnerability

Open a [private security advisory](https://github.com/mateof/siyuan-remote/security/advisories/new) rather than a public issue.

## Threat model

This app deliberately stores the password that opens your entire knowledge base, indefinitely. That is the requested behaviour, and it is worth being explicit about what it does and does not protect against.

### What is protected

- The lock screen password is encrypted with AES-256/GCM under a key generated in the Android Keystore, hardware backed on devices that support it. The key never leaves the device and cannot be extracted by another app.
- The encrypted password is excluded from cloud backup and device-to-device transfer, so it does not travel with a phone restore.
- The optional app lock (on by default) puts biometrics or the device credential in front of the WebView.
- User installed certificate authorities are trusted, so a self hosted HTTPS setup can be verified properly. There is no "ignore certificate errors" option, on purpose.

### What is not protected

- **A rooted or compromised device.** Keystore raises the cost of extraction; it does not make it impossible on a device an attacker fully controls.
- **Someone with your unlocked phone and the app lock disabled.** That is what the lock is for.
- **Plain HTTP.** If you connect over `http://`, the password crosses the network in the clear on every login and so does every note you read or write. Acceptable on a LAN you trust, not acceptable over the open internet. Use HTTPS or a VPN.
- **A weak password.** It is a single shared secret with no rate limiting beyond the kernel's captcha counter. Make it long.

### In app updates

The app holds `REQUEST_INSTALL_PACKAGES` so it can offer to install a newer release it downloaded from this project's GitHub releases. It only ever downloads from `api.github.com` and the `browser_download_url` GitHub returns, over HTTPS, and the install always goes through the system installer, which shows you what is about to happen and verifies the signature against the installed app. Turning the setting off stops every check.

### The clipboard bridge

To make pasting with formatting possible at all, the app exposes a small
JavaScript interface that can read the device clipboard, and injects a script
that routes `navigator.clipboard.read` through it.

`addJavascriptInterface` cannot be scoped to an origin, so the bridge itself
checks the page it is serving and returns nothing unless that page belongs to
the configured server. The WebView only ever loads that server, because outbound
links leave for a custom tab, but the check exists so a redirect cannot turn
into clipboard access. It is read only: nothing writes to the clipboard through
it.

Turning **Paste with formatting** off in Settings removes the injection.

### Design notes

The Keystore key is created with `setUserAuthenticationRequired(false)`. Binding it to biometrics would prevent background capture from the share sheet, widget and notification while the phone is locked, which is most of the point of those features. The trade is stated here rather than hidden.

Sign out deletes the stored password, clears the cookie store and calls `logoutAuth` on the server. It is the only action that does.
