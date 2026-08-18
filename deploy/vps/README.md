# VPS deployment

This package runs the Java API, SQLite database, and bundled responsive web app behind Caddy. Caddy obtains and renews HTTPS certificates automatically. The backend is not exposed directly to the internet.

## Server requirements

- Ubuntu 22.04/24.04 or another host with Docker Engine and the Compose plugin
- a DNS `A` record (and correct `AAAA` record, if present) pointing to the VPS
- inbound TCP ports 80 and 443; UDP 443 is optional but enables HTTP/3
- at least 1 GB RAM; 2 GB is preferable when PDF or AI-source processing is used

## First deployment

Copy the repository to the VPS, then run:

```bash
cd /opt/carebinder
sudo ./deploy/vps/bootstrap-ubuntu.sh
cp deploy/vps/.env.example deploy/vps/.env
chmod 600 deploy/vps/.env
nano deploy/vps/.env
./deploy/vps/deploy.sh
```

Set `APP_DOMAIN` and `ACME_EMAIL`. `GOOGLE_CLIENT_IDS` is public OAuth configuration but belongs in the backend allowlist. `BYTEZ_API_KEY` and `BYTEZ_PROVIDER_KEY` are backend-only secrets. The web app is bundled into the backend image, so no separate web build or web container is required.

For Google sign-in, add `https://APP_DOMAIN` as an authorized JavaScript origin in Google Cloud and use the same Web OAuth client ID in `GOOGLE_CLIENT_IDS`. Email/password registration works when Google and Bytez are unset.

## Updates and operations

From the repository root on the server:

```bash
./deploy/vps/deploy.sh       # rebuild and perform a rolling container replacement
./deploy/vps/status.sh       # service state and recent logs
./deploy/vps/backup.sh       # consistent, short-downtime SQLite volume backup
```

Backups default to `deploy/vps/backups/`. Copy them to encrypted off-server storage and test restoration before accepting real user data. `deploy.sh` does not delete the database volume.

## Production boundary

HTTPS protects traffic, but this single-VPS SQLite deployment is still an early private-beta architecture. Before accepting sensitive real-world care data, add encrypted VPS disks and backups, automated off-site backups, monitoring, rate limiting, password reset/email verification, tested disaster recovery, vendor/privacy review, and operating-system security updates.
