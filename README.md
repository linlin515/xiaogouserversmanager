# 小狗服务器（XiaoGou Server Manager）

多台 Linux 服务器实时监控与轻量运维 Android App，支持 **SSH 免部署直连** 与 **可选轻量 Python Agent** 混合采集模式。

## 核心特性

- **双模采集与自动降级（Hybrid）**：
  - `SSH_ONLY`：无需在服务器安装任何软件，支持密码、OpenSSH/PEM 私钥（含口令）及严格 TOFU 主机指纹校验。
  - `AGENT_ONLY`：纯 Python 3 标准库零依赖守护进程（`agent/server_eyes_agent.py`），内置 SQLite 48 小时历史环形缓冲、Bearer Token 鉴权与防爆破速率限制。
  - `HYBRID`：优先从 Agent 获取低开销实时指标与长周期曲线，失败时自动无缝回退至 SSH。
- **多机看板与详情洞察**：
  - 集群状态脉冲条、分组筛选、关键词搜索、节点置顶/暂停采集、自适应平板双栏（NavigationRail + Master-Detail）。
  - 实时 CPU / 内存 / Swap / 磁盘（含 Inode 占用）/ 磁盘 I/O 吞吐 / 网络 RX·TX / Load1 / SoC 温度监控与 15m~48h 历史趋势图。
  - 详情页 KPI 速览、可切换指标（CPU·内存/磁盘/温度/磁盘 I/O/网络/Load）趋势图：真实时间轴、Y 轴刻度、告警阈值虚线、拖动读数、实时增量追加；近期告警时间线与 Top 进程排序。
- **容器、服务与轻量运维**：
  - Docker 容器与 systemd 服务列表、端口映射展示、启动/停止/重启（二次确认）、实时日志查看（50/150/300 行切换与刷新）、只读白名单诊断命令终端。
- **闭环告警与加密配置迁移**：
  - 全局默认 + 单节点独立覆盖规则、离线宽限、冷却去重、指标回落自动恢复与系统通知联动、免打扰时段（DND）。
  - Android Keystore AES-256-GCM 硬件级本地凭据加密；PBKDF2 + AES-256-GCM 加密备份导出与事务化合并/替换导入。

## 目录结构

```text
├── app/                          # Android App (Kotlin + Jetpack Compose + Material 3)
│   └── src/main/java/com/xiaogou/serverseyes/
│       ├── core/                 # 模型、加密、SSH、Agent 客户端、备份、只读诊断校验、设计系统
│       ├── data/                 # Room (v2)、DataStore、告警引擎、Repository
│       ├── service/              # 通知渠道/动作接收器、ForegroundService、WorkManager
│       └── ui/                   # 自适应主界面、看板、详情、编辑、告警中心、设置
├── agent/                        # 可选服务端 Python 3 Agent 与 systemd 安装脚本
├── docs/prototypes/              # UI 设计说明与交互原型门禁记录
└── .github/workflows/ci.yml      # GitHub Actions 自动化单测与构建流水线
```

## 快速构建与测试

### 1. 运行单元测试与 Lint
```bash
python3 -m unittest agent/test_agent.py -v
./gradlew :app:testDebugUnitTest :app:lintDebug
```

### 2. 构建签名 Release APK
复制 `.env.example` 为 `.env` 并填写对应渠道签名信息后执行：
```bash
./gradlew :app:assembleRelease -Pchannel=common
```

### 3. 可选：部署服务端 Agent
```bash
sudo bash agent/install.sh 19090 <your-strong-token>
```
详见 [agent/README.md](agent/README.md)。
