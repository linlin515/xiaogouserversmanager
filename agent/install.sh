#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="/opt/xiaogou-server-eyes-agent"
PORT="${1:-19090}"
TOKEN="${2:-$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)}"

if [[ $EUID -ne 0 ]]; then
  echo "请使用 root 权限运行: sudo bash install.sh [port] [token]"
  exit 1
fi

mkdir -p "$INSTALL_DIR"
cp "$(dirname "$0")/server_eyes_agent.py" "$INSTALL_DIR/server_eyes_agent.py"
chmod +x "$INSTALL_DIR/server_eyes_agent.py"

cat >/etc/systemd/system/xiaogou-eyes-agent.service <<EOF
[Unit]
Description=XiaoGou Servers Eyes Lightweight Monitoring Agent
After=network.target

[Service]
Type=simple
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/python3 $INSTALL_DIR/server_eyes_agent.py --host 0.0.0.0 --port $PORT --token $TOKEN --db-path $INSTALL_DIR/metrics.db
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now xiaogou-eyes-agent.service

echo "=================================================="
echo "XiaoGou Agent 安装并启动成功！"
echo "端口: $PORT"
echo "Bearer Token: $TOKEN"
echo "建议通过 Tailscale/WireGuard 内网、HTTPS 反向代理或 SSH 端口转发访问。"
echo "=================================================="
