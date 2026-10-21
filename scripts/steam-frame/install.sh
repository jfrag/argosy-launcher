#!/bin/bash
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
install -Dm755 "$here/lepton-padbridge" "$HOME/.local/bin/lepton-padbridge"
install -Dm644 "$here/lepton-padbridge.service" "$HOME/.config/systemd/user/lepton-padbridge.service"
systemctl --user daemon-reload
systemctl --user enable --now lepton-padbridge.service
systemctl --user --no-pager status lepton-padbridge.service | head -5
