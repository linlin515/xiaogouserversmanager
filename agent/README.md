# XiaoGou Servers Eyes — 可选轻量服务端 Agent

本目录提供**可选**的服务端轻量守护进程 `server_eyes_agent.py`（纯 Python 3 标准库，零外部依赖）。

- **不安装也能用**：App 默认支持 SSH 免部署直连，无需在服务器安装任何程序。
- **安装后增强**：在需要长周期历史趋势（24h–48h 曲线，手机息屏期间持续采集）与更低采集开销的节点上部署 Agent，配合 App 的 **混合模式（Hybrid）** 使用。
- **v1.4.0 起**：历史环形缓冲新增磁盘使用率、CPU 温度、磁盘读写速率、Swap 用量、Load5/15 字段；旧版数据库自动 `ALTER TABLE` 补列，无需手动迁移。

## 快速运行（手动/测试）
```bash
python3 server_eyes_agent.py --port 19090 --token your-secret-token
```

如需在 App 中通过 Agent 执行容器/服务重启，可显式追加 `--enable-actions` 参数；未开启时仅允许只读监控与日志查看。

## 安装为 Systemd 服务
```bash
sudo bash install.sh 19090
```

## 安全建议
1. 务必设置强随机 `--token`。
2. 避免直接把 HTTP 端口裸露在公网：推荐绑定到 `127.0.0.1` 配合 Nginx/Caddy HTTPS 反向代理，或通过 Tailscale / WireGuard 内网 IP 访问。
