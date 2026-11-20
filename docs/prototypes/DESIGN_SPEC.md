# XiaoGou Servers Eyes — 产品级 UI 设计说明（遵循 UI_DESIGN_GUIDELINES.md §18）

- **产品名称**：小狗服务器之眼（XiaoGou Servers Eyes）
- **产品类型**：多服务器实时监控与轻量运维 Android App
- **目标用户**：同时管理多台 Linux VPS / 自建主机 / 容器节点的开发者与运维人员
- **核心使用场景**：多机实时状态巡检、单机性能瓶颈排查、Docker/Systemd 服务状态确认与日志查看、阈值告警提醒
- **支持主题**：浅色 / 深色 / 跟随系统（全套语义 Token 映射，非简单反色）
- **目标设备**：Android 手机（竖屏/横屏）、折叠屏与平板（Adaptive NavigationRail + 双栏分屏）
- **技术栈**：Kotlin + Jetpack Compose + Material 3（Strict `dp` / `sp`、Edge-to-Edge WindowInsets、48dp 最小触控区域、TalkBack 语义标签）

## 三套备选视觉方向

| 方案 | 风格定位 | 品牌主色（浅/深） | 看板信息密度 | 适用偏好 |
|---|---|---|---|---|
| **方案 A · Ops Precision** | 冷静专业运维台 | `#2563EB` / `#5B93FF` | 中高（双列卡片 + 实时折线微图 + 集群脉冲条） | 兼顾多机扫视与曲线趋势 |
| **方案 B · Material 3 Bento** | 现代极简工具风（贴近规范 §4.2） | `#315EFB` / `#7894FF` | 舒朗（Bento 摘要卡 + 异常焦点置顶展开） | 看重呼吸感与原生 Material 质感 |
| **方案 C · Matrix Telemetry** | 高密度终端矩阵 | `#0D9488` / `#2DD4BF` | 高（等宽数字 + 单屏 6~10 台紧凑状态矩阵） | 节点较多、偏好等宽数据流 |

> **门禁说明**：待用户在交互原型中选定最终方案后，再基于对应 Token 与布局结构进入阶段三 Compose UI 开发。
