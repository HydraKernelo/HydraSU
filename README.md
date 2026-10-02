# HydraSU 🐍

**基于 KernelSU-Next 的自研 root 管理器** —— LKM 全 KMI、浏览器寄生控制台、极致隐藏（无管理器模式）、深度隐匿。

> 免费 · 开源 · 无广告 · 遵循 GPL-3.0 · [Telegram 频道 @hydrasuzzz](https://t.me/hydrasuzzz)

---

## ✨ 特性

### 🦾 Root 能力（LKM 模式，不换内核）
- **全 KMI 覆盖**：GKI 2.0 内核 5.10 ~ 6.18（android12 ~ android17），每条 KMI 独立编译 `.ko`
- 修补 boot 即刷即用，管理器内置对应 KMI 的内核模块，修补时自动匹配
- 证书信任链：管理器签名即凭证，无需担心包名变动

### 🔥 极致隐藏 · 无管理器模式（v2.0 新增）
- 设置页一键开启，**自动刷入两个内置模块**：
  - **AutoStart** —— 开机自检，静默接管本地服务（含 MIUI 冻结豁免）
  - **独立控制台** —— 自带全功能 busybox 的 HTTP 服务，不依赖管理器
- 刷入后删除管理器 App：桌面、应用列表、包名扫描全面消失
- 任意浏览器访问 `http://127.0.0.1:38214/`，输入 Token 即可管理 root：
  **授权 / 模块 / 日志 / 终端** 全功能不缩水
- Token 持久化于 `/data/adb/hydra/token`，重装、重启不漂移
- 与「寄生工作台」互斥，二者不可同时开启

### 🕶️ 深度隐匿
- **模块自隐藏**：`/proc/modules`、`lsmod`、`/sys/module/kernelsu` 全部无痕
- **内核名伪装**：可自定义 `uname -r` 显示内容，或按内核版本自动生成标准命名（配置驱动，双向实时切换）
- **路径隐藏**：`/data/adb` 对无权限应用返回 ENOENT + 目录列表过滤（移植自 FolkPatch 的 syscall 钩子手法）
- **dmesg 静默**：驱动日志标签中性化 + info 级全静默
- **应用伪装**：一键切换「系统更新」外观 / 隐藏桌面图标

### 🌐 寄生控制台（浏览器管理）
- 管理器内启动本地服务（`127.0.0.1:38214`，Token 路径鉴权）
- 手机浏览器打开即得完整管理台：**状态 / 超级用户 / 模块 / 日志 / 关于**
- 超级用户页：真实应用图标 + 应用名/包名双搜索 + 点击立即授权生效
- 模块管理：刷入 zip / 启停 / 执行 action.sh / 卸载
- 开关即插即用：关闭后服务立即停止（自启与保活拉起均被拦截）

### 🧩 模块生态
- 完整支持 KernelSU 模块格式（Magic Mount）
- 模块图标 / action.sh / webroot 均原生支持

---

## 📲 安装

1. 在 [Releases](../../releases) 下载管理器 APK 并安装
2. 管理器 → 安装 → 修补 boot 镜像（自动内嵌对应 KMI 的内核模块）→ 刷入 → 重启
3. 打开管理器，看到「已 root」即完成

> 首次开启「隐藏内核名字」时会提示安装 SELinux 修复模块（放行内核读取配置），点确认后重启生效。

---

## 🌐 两种浏览器管理模式

| | 寄生工作台 | 极致隐藏 |
|---|---|---|
| 管理器 App | 保留 | **删除** |
| 服务提供方 | 管理器进程内服务 | 独立控制台模块（自带 busybox） |
| 桌面痕迹 | 有（可配合隐藏图标） | 无 |
| 适用场景 | 日常使用 | 交机 / 检测 / 极致隐蔽 |

- **寄生工作台**：设置 → 寄生工作台开启 → 复制完整地址 → 浏览器打开，支持 PWA 添加到主屏幕
- **极致隐藏**：设置 → 极致隐藏开启 → 确认自动刷入双模块 → 重启 → 回设置页复制「控制台访问密钥」→ 卸载管理器 → 浏览器访问 `127.0.0.1:38214`

---

## 🔨 从源码构建

```bash
git clone https://github.com/HydraKernelo/HydraSU.git
cd HydraSU
git checkout dev
# Android Studio 或命令行：
./gradlew :manager:app:assembleRelease
```

内核模块（全 KMI）由 GitHub Actions 自动构建（Build Manager CI）。

---

## 🙏 致谢

| 项目 | 致谢 |
|---|---|
| [KernelSU](https://github.com/tiann/KernelSU) | tiann —— 原版内核级 root 方案 |
| [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next) | rifsxd 及社区 —— Next 分支基座 |
| [FolkPatch](https://github.com/LyraVoid/FolkPatch) | 路径隐藏实现参考 |
| HydraKernelo | HydraSU 维护者（与 青凤） |
| QQ 群友 | happy / 我的错 / 晚雾渡星河 / 沐神 / 爱吃肉的棒男孩 |

---

## 📄 许可证

[GPL-3.0](LICENSE) —— HydraSU 为自由软件，基于 KernelSU-Next 修改，遵循同等开源协议。
