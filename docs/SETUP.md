# Server setup

SiYuan Remote does not ship a kernel. It connects to one you run. This page covers getting that kernel into a state where the app can reach it safely.

## 1. Run the kernel

The usual deployment is Docker.

```yaml
# docker-compose.yml
services:
  siyuan:
    image: b3log/siyuan:latest
    container_name: siyuan
    restart: unless-stopped
    command:
      - --workspace=/siyuan/workspace
      - --accessAuthCode=CHANGE_ME
    ports:
      - "6806:6806"
    volumes:
      - ./workspace:/siyuan/workspace
    environment:
      - TZ=Europe/Madrid
      # Must match the uid/gid that owns ./workspace
      - PUID=1000
      - PGID=1000
```

```bash
docker compose up -d
```

Check it answers:

```bash
curl -s http://localhost:6806/api/system/version
# {"code":0,"msg":"","data":"3.x.y"}
```

## 2. Set an access authorization code

**This is mandatory, not optional.** The kernel explicitly refuses any non-local client when no access authorization code is configured:

> Auth failed: for security reasons, please set [Lock screen password] when using non-127.0.0.1 access

You can set it with `--accessAuthCode=...` as above, or inside SiYuan under **Settings → About → Access authorization code**.

If the app shows *"Your server has no access authorization code set"*, this is what it means.

Pick something long. It is the only thing between the network and your entire knowledge base, and the app will store it on the phone indefinitely.

## 3. Make the kernel reachable from the phone

### On your own network

Bind the container to the host and use the host's LAN address:

```
http://192.168.1.50:6806
```

Plain HTTP over a trusted LAN is what most people do, and the app allows cleartext for exactly this reason. Everything, including the access code on login, crosses your LAN unencrypted. If you do not trust your LAN, use HTTPS.

### From outside: Tailscale (simplest)

Install Tailscale on the server and on the phone, then use the tailnet name:

```
https://siyuan.your-tailnet.ts.net:6806
```

Tailscale supplies a valid certificate through `tailscale cert` / Serve, so no extra trust configuration is needed on the phone. Put the LAN address in **Server address** and the tailnet address in **Fallback address**: the app probes the first, falls back to the second, and remembers which one worked.

### From outside: reverse proxy

Caddy makes this a two line job with automatic Let's Encrypt certificates:

```
siyuan.example.com {
    reverse_proxy 127.0.0.1:6806
}
```

With a reverse proxy in front, bind the kernel to `127.0.0.1:6806` rather than `0.0.0.0:6806` so it is not reachable directly, bypassing the proxy.

WebSockets must pass through. Caddy's `reverse_proxy` handles the upgrade automatically; in nginx you need the usual `Upgrade` and `Connection` headers, otherwise the editor loads but never receives kernel events.

### Self signed certificates

The app trusts **user installed certificate authorities** (see `res/xml/network_security_config.xml`). If you run your own CA:

1. Export the CA certificate (not the leaf).
2. Copy it to the phone and install it under **Settings → Security → Encryption & credentials → Install a certificate → CA certificate**.
3. Use the `https://` address in the app.

The app deliberately has no "ignore certificate errors" switch. A prompt that trains you to accept any certificate is worse than no HTTPS at all.

## 4. Connect the app

1. **Server address**: `http://192.168.1.50:6806`
2. **Fallback address** (optional): `https://siyuan.your-tailnet.ts.net`
3. **Access authorization code**: the value from step 2.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "No SiYuan kernel answered at that address" | Wrong host or port, kernel bound to `127.0.0.1`, firewall | `curl http://ADDRESS/api/system/version` from another machine |
| "Your server has no access authorization code set" | `--accessAuthCode` missing | Set it and restart the kernel |
| "The server rejected that access authorization code" | Typo, or the code changed on the server | Re-enter it in the app |
| "Too many failed attempts: the server now asks for a captcha" | The kernel counts wrong attempts globally | The app renders the captcha; enter it once and the counter resets |
| Editor loads but never updates | WebSocket not proxied | Fix the `Upgrade` headers in your reverse proxy |
| Editor renders oddly | Old Android System WebView | Update it from the Play Store; Settings shows the installed version |
| Mixed content warnings on HTTPS | Assets served over plain HTTP | Fix the origin, or enable **Allow mixed content** as a last resort |

## What this setup does not give you

Offline access. The workspace lives on the server; with no route to the server there are no notes. If you need to write on a plane, run the official SiYuan app with its own kernel and sync it to your server through S3 (MinIO) or WebDAV. The two approaches can coexist on the same phone.
