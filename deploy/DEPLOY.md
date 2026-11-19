# Deploying Kitepost (GitHub release → server)

The build happens on GitHub, not on the server. Pushing a version tag builds the backend jar and the web
app and attaches them to a GitHub Release. On the server, `./deploy/deploy.sh <version>` downloads that
release and restarts two PM2 processes. Nothing is compiled on the server.

```
developer PC ── git tag v1.0.0 && git push origin v1.0.0 ──▶ GitHub Actions (.github/workflows/release.yml)
                                                              builds backend.jar + frontend.tar.gz
                                                              └──▶ GitHub Release v1.0.0
server ── ./deploy/deploy.sh v1.0.0 ── gh release download ──▶ kitepost_backend/  kitepost_frontend/  ── pm2 restart
```

It shares the site folder (and the domain) with another project. Everything Kitepost uses has its own name:

| What | Name / value |
|---|---|
| Folders in the site folder | `kitepost_backend/`, `kitepost_frontend/`, `deploy/` |
| PM2 processes | `kitepost-backend`, `kitepost-frontend` |
| Backend port / web app port | `8087` / `3016` (localhost only; change in `deploy/ecosystem.config.js`) |
| URL | `https://<domain>/kitepost` (API at `/kitepost/api`) |
| Database | MySQL `kitepost` (the app creates the tables itself) |
| Cookies | `KITEPOST_SESSION`, `KITEPOST-XSRF` (different from the other apps) |

## One-time setup on the server

Do these once, in PuTTY, as the user that runs PM2. `SITE` is the site folder.

### 1. The GitHub CLI can read the releases
The repository is public, so `gh release download` works without signing in. If it is ever made private, run `gh auth login` once.

### 2. The database (empty; Kitepost creates the tables on first start)
```sql
-- sudo mysql   (or mysql -u root -p)
CREATE DATABASE kitepost CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'kitepost'@'localhost' IDENTIFIED BY '<the password from kitepost-server-credentials.txt>';
GRANT ALL PRIVILEGES ON kitepost.* TO 'kitepost'@'localhost';
FLUSH PRIVILEGES;
```

### 3. The folders, the deploy scripts and the settings
```bash
SITE=/var/www/Vhost/jjlltdnext.desss-portfolio.com
cd $SITE
mkdir -p deploy kitepost_backend
cd deploy
for f in deploy.sh deploy.conf.example ecosystem.config.js.example; do
  curl -fsSLO https://raw.githubusercontent.com/chandlerdev/kitepost/main/deploy/$f
done
chmod +x deploy.sh
cp deploy.conf.example deploy.conf
cp ecosystem.config.js.example ecosystem.config.js
```
Upload the settings file as `$SITE/kitepost_backend/.env` (WinSCP). It holds the database password,
the admin sign-in, the search / Claude / mail keys, and `PUBLIC_URL`. Then lock it down:
```bash
chmod 600 $SITE/kitepost_backend/.env
```
Check that ports 8087 and 3016 are free: `ss -ltn | grep -E ':(8087|3016)\b'` should print nothing.

The site folder is another project's git repository. To keep Kitepost's folders out of its `git status`
without changing that project, run once: `printf 'kitepost_backend/\nkitepost_frontend/\ndeploy/\n' >> $SITE/.git/info/exclude`

### 4. Apache: send `/kitepost` to Kitepost
In `/etc/apache2/sites-enabled/jjlltdnext…conf`, inside the `:443` VirtualHost, add these lines **above**
the catch-all `ProxyPass /  http://127.0.0.1:3013/` (Apache uses the first matching line):
```apache
    # ---- Kitepost ----
    RequestHeader set X-Forwarded-Proto "https"
    ProxyPass        /kitepost/api  http://127.0.0.1:8087/api
    ProxyPassReverse /kitepost/api  http://127.0.0.1:8087/api
    ProxyPass        /kitepost      http://127.0.0.1:3016/kitepost
    ProxyPassReverse /kitepost      http://127.0.0.1:3016/kitepost
    # ---- end Kitepost ----
```
Then: `sudo apache2ctl configtest && sudo systemctl reload apache2`
(`RequestHeader` needs `mod_headers`: `sudo a2enmod headers` if the test complains.)

## Every release

On your PC:
```bash
git add -A && git commit -m "..." && git push origin main
git tag v1.0.1 && git push origin v1.0.1      # builds the release (about 5 to 8 minutes)
```
Watch it at https://github.com/chandlerdev/kitepost/actions. When it is green the Release exists.

On the server:
```bash
cd /var/www/Vhost/jjlltdnext.desss-portfolio.com
./deploy/deploy.sh v1.0.1
```
The backend takes about a minute to come up. Open `https://jjlltdnext.desss-portfolio.com/kitepost`.

**Roll back:** run the same command with the older version, for example `./deploy/deploy.sh v1.0.0`.

## When something is wrong
| Symptom | Look at |
|---|---|
| `pm2 ls` shows `kitepost-backend` restarting | `pm2 logs kitepost-backend --lines 50` (wrong database password, or port 8087 taken) |
| Page loads but sign-in says "Request failed (HTTP 502/503)" | the backend is still starting, or not running; wait a minute, then `pm2 ls` |
| Sign-in works but you are signed out at once | `COOKIE_SECURE=true` needs HTTPS and the `X-Forwarded-Proto` line in Apache |
| Blank page or missing styles | the release was built with another `BASE_PATH` than `/kitepost` |
| Email opens never appear | `PUBLIC_URL` in `kitepost_backend/.env` must be the public address including `/kitepost` |
| Changed `kitepost_backend/.env` | `pm2 restart kitepost-backend --update-env` (or run `deploy.sh` again) |
| Settings > Mail configuration says passwords can't be saved | add `MAIL_ENCRYPTION_KEY=<long random value>` to `kitepost_backend/.env`, then restart as above |
| Saved mailbox passwords stopped working after a restart | `MAIL_ENCRYPTION_KEY` is missing or was changed: put the old one back, or enter the passwords again in Settings |

## Mail (Settings > Mail configuration)
Admins set the sending mailbox (SMTP) and the reply mailbox (IMAP) in the app, with a Test button for each.
The passwords are saved in the database encrypted with AES-256-GCM; the key is `MAIL_ENCRYPTION_KEY` in
`kitepost_backend/.env` and is never stored in the database. What is saved in Settings wins over the
`SMTP_` / `IMAP_` lines in `.env`; "Use server settings" goes back to those.

## Notes
- **The base path is baked into the web app at build time** (`/kitepost`). To serve it somewhere else, set a
  repository variable `BASE_PATH` (Settings → Secrets and variables → Actions → Variables) before tagging.
- Secrets never go into git: `.env` files are ignored, and `kitepost_backend/.env` exists only on the server.
- The release workflow runs the backend's unit tests, so a failing test stops a release from being published.
