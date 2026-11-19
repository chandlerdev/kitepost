#!/usr/bin/env bash
#
# Puts a release built on GitHub (.github/workflows/release.yml) live on this
# server, without building anything here:
#
#   cd /var/www/Vhost/jjlltdnext.desss-portfolio.com
#   ./deploy/deploy.sh v1.0.0
#
# Going back is the same command with an older version.
#
# What it changes (all inside the site folder, next to the other project):
#   - kitepost_backend/kitepost-backend.jar, in place (the old one is kept as .previous);
#   - kitepost_frontend/, the web app's server;
#   - then restarts the two PM2 processes (starting them the first time).
# It never touches the database or kitepost_backend/.env - the backend reads its database,
# logins, keys and mail settings from there when it starts (deploy/ecosystem.config.js loads it).
#
# Needs, once: the GitHub CLI (gh), kitepost_backend/.env, and deploy/deploy.conf and
# deploy/ecosystem.config.js - copied from the .example files beside this script. See DEPLOY.md.
set -euo pipefail

VERSION="${1:?Usage: ./deploy/deploy.sh <version>, for example ./deploy/deploy.sh v1.0.0}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE_DIR="$(dirname "$HERE")"

for f in deploy.conf ecosystem.config.js; do
  if [ ! -f "$HERE/$f" ]; then
    echo "ERROR: no $f in $HERE - copy $f.example to it and fill it in (see DEPLOY.md)." >&2
    exit 1
  fi
done
# shellcheck disable=SC1091
source "$HERE/deploy.conf"   # REPO, BACKEND_NAME, FRONTEND_NAME, BACKEND_DIR, FRONTEND_DIR, BACKEND_JAR

if [ ! -f "$SITE_DIR/$BACKEND_DIR/.env" ]; then
  echo "ERROR: no $BACKEND_DIR/.env in $SITE_DIR - the backend needs its database and other settings there." >&2
  exit 1
fi
JAR="$SITE_DIR/$BACKEND_DIR/$BACKEND_JAR"
WEB="$SITE_DIR/$FRONTEND_DIR"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "==> Downloading $VERSION from $REPO"
gh release download "$VERSION" --repo "$REPO" --dir "$TMP" \
  --pattern 'backend.jar' --pattern 'frontend.tar.gz'

# Unpacked first, beside the running copy, so a broken download stops here
# with the site still on the old version.
mkdir -p "$(dirname "$JAR")"
rm -rf "$WEB.new"
mkdir "$WEB.new"
tar -xzf "$TMP/frontend.tar.gz" -C "$WEB.new"
test -f "$WEB.new/server.js" || { echo "ERROR: the web app package has no server.js" >&2; exit 1; }

echo "==> Backend"
# Copied beside the jar and renamed over it: a running Java keeps reading the
# old file until the restart, instead of having it change underneath it.
if [ -f "$JAR" ]; then cp -p "$JAR" "$JAR.previous"; fi
cp "$TMP/backend.jar" "$JAR.new"
mv -f "$JAR.new" "$JAR"

echo "==> Web app"
rm -rf "$WEB.old"
if [ -d "$WEB" ]; then mv "$WEB" "$WEB.old"; fi
mv "$WEB.new" "$WEB"

echo "==> Restarting"
# startOrRestart starts a process that is not running yet and restarts one that is;
# --update-env makes it re-read kitepost_backend/.env (ecosystem.config.js loads it).
for name in "$BACKEND_NAME" "$FRONTEND_NAME"; do
  pm2 startOrRestart "$HERE/ecosystem.config.js" --only "$name" --update-env
done
rm -rf "$WEB.old"

pm2 save
echo "$VERSION" > "$SITE_DIR/$FRONTEND_DIR/VERSION"
echo "==> Done: $VERSION is live (the backend takes about a minute to come up)."
echo "    Check:  pm2 ls   |   pm2 logs $BACKEND_NAME --lines 30"
