#!/usr/bin/env bash
# Omalauncher Ormus: each workboard space mirrors now: its board tile (the sessions tile with three lines changed:
# its name, its page, and `only`, the workboard it shows) and its comms tile (the comms tile with the same three).
# Run from the repo root after editing 10-sessions.owl or 12-comms.owl; OrmusWidgetsTest pins the copies to them,
# so a copy edited by hand fails the gate.
#   scripts/ormus-board-tiles.sh
set -euo pipefail
dir="${1:-app/src/main/assets/ormus/widgets}"
# Each workboard space (Ormus.SPACES of the build with the workboards) holds its two tiles on its first page:
# raven 101, ormus 201, alqvimia 301, clients 401. Under the five older spaces those pages belong to money, work,
# mind and world: the tiles ship only with the build that has the workboards.
boards=(raven:101 ormus:201 alqvimia:301 clients:401)
copy() { # <source file> <widget name> <copy prefix> <board> <page>
  sed -e "s/^widget \"$2\" {\$/widget \"$2-$4\" {/" \
      -e "s/^  page: 1\$/  page: $5/" \
      -e "s/^  let only = \"\"\$/  let only = \"$4\"/" \
      "$dir/$1" > "$dir/$3-$4.owl"
  [ "$(diff "$dir/$1" "$dir/$3-$4.owl" | grep -c '^>')" = 3 ] || { echo "$3-$4.owl: not three lines changed" >&2; exit 1; }
}
for entry in "${boards[@]}"; do
  board="${entry%%:*}"
  page="${entry##*:}"
  copy 10-sessions.owl sessions 11-board "$board" "$page"
  copy 12-comms.owl comms 13-comms "$board" "$page"
done
echo "board tiles: ${#boards[@]} boards, $(( ${#boards[@]} * 2 )) files written in $dir"
