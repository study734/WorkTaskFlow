#!/bin/sh
set -eu

cd /opt/totaskflow
docker compose --env-file runtime.env.current -f compose.yml run --rm certbot renew --quiet
docker compose --env-file runtime.env.current -f compose.yml exec -T web nginx -s reload
