#!/bin/sh
# Omalauncher Ormus: lets Sun reach this Linux over SSH on port 2223 with the
# keys in $1, and gives it ssh and mosh to Sun as "sun", sg as "sg" and Mars as "mars". Run by OrmusBridge,
# which restarts it if sshd exits.
set -u
if ! command -v sshd >/dev/null || ! command -v mosh >/dev/null; then
    # Plain -S first; -Syu only when the package database is stale (Arch has no partial upgrades).
    pacman -S --noconfirm --needed openssh mosh || pacman -Syu --noconfirm --needed openssh mosh || exit 1
fi
mkdir -p ~/.ssh && chmod 700 ~/.ssh
touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys
while read -r key; do
    [ -n "$key" ] && ! grep -qxF "$key" ~/.ssh/authorized_keys && echo "$key" >> ~/.ssh/authorized_keys
done < "$1"
[ -f ~/.ssh/id_ed25519 ] || ssh-keygen -q -t ed25519 -N '' -C 'root@saturn-omalauncher-ormus' -f ~/.ssh/id_ed25519
grep -q '^Host sun$' ~/.ssh/config 2>/dev/null || printf 'Host sun\n    HostName 100.92.213.61\n    User ormus\n' >> ~/.ssh/config
# sg runs Ormus's herdr agents; the mercurio widget reads them.
grep -q '^Host sg$' ~/.ssh/config 2>/dev/null || printf 'Host sg\n    HostName 100.66.108.108\n    User ormus\n' >> ~/.ssh/config
# Mars runs Ormus's Hermes agents in herdr too.
grep -q '^Host mars$' ~/.ssh/config 2>/dev/null || printf 'Host mars\n    HostName 100.107.202.72\n    User aiadmin\n' >> ~/.ssh/config
# The smart notifications widget's scorer (asks the local model on Mars).
mkdir -p ~/.local/bin && install -m 755 /tmp/ormus-notif-score ~/.local/bin/notif-score
ssh-keygen -A
exec /usr/bin/sshd -D -e -p 2223 -o PasswordAuthentication=no -o PermitRootLogin=prohibit-password
