#!/usr/bin/env bash
set -euo pipefail

mongosh --host mongo:27017 --quiet <<'JS'
try {
  const status = rs.status();
  if (status.ok === 1) {
    quit(0);
  }
} catch (error) {
}

rs.initiate({
  _id: "rs0",
  members: [
    { _id: 0, host: "mongo:27017" }
  ]
});
JS
