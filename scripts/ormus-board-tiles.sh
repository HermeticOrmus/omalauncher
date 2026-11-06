#!/usr/bin/env bash
# Omalauncher Ormus: the board tiles are the sessions tile with three lines changed (its name, its page, and
# `only`, the workboard it shows). Run from the repo root after editing 10-sessions.owl; OrmusWidgetsTest pins
# the copies to it, so a board tile edited by hand fails the gate.
#   scripts/ormus-board-tiles.sh
set -euo pipefail
dir="${1:-app/src/main/assets/ormus/widgets}"
# Each workboard space (Ormus.SPACES) holds its board tile on its first page: raven 101, ormus 201, ...
boards=(raven:101 ormus:201 alqvimia:301 clients:401)
for entry in "${boards[@]}"; do
  board="${entry%%:*}"
  page="${entry##*:}"
  sed -e "s/^widget \"sessions\" {\$/widget \"board-$board\" {/" \
      -e "s/^  page: 1\$/  page: $page/" \
      -e "s/^  let only = \"\"\$/  let only = \"$board\"/" \
      "$dir/10-sessions.owl" > "$dir/11-board-$board.owl"
  [ "$(diff "$dir/10-sessions.owl" "$dir/11-board-$board.owl" | grep -c '^>')" = 3 ] || { echo "11-board-$board.owl: not three lines changed" >&2; exit 1; }
done
echo "board tiles: ${#boards[@]} written in $dir"
