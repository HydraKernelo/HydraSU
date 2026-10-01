# HydraSU 🐍

**基于 KernelSU-Next 的自研 root 管理器** —— LKM 全 KMI、浏览器寄生控制台、深度隐匿。

> 免费 · 开源 · 无广告 · 遵循 GPL-3.0

---

## ✨ 特性

### 🦾 Root 能力（LKM 模式，不换内核）
- **全 KMI 覆盖**：GKI 2.0 内核 5.10 ~ 6.18（android12 ~ android17），每条 KMI 独立编译 `.ko`
- 修补 boot 即刷即用，管理器内置对应 KMI 的内核模块，修补时自动匹配
- 证书信任链：管理器签名即凭证，无需担心包名变动

### 🕶️ 深度隐匿
- **模块自隐藏**：`/proc/modules`、`lsmod`、`/sys/module/kernelsu` 全部无痕
- **内核名伪装**：可自定义 `uname -r` 显示内容，或按内核版本自动生成标准命名（配置驱动，双向实时切换）
- **路径隐藏**：`/data/adb` 对无权限应用返回 ENOENT + 目录列表过滤（移植自 FolkPatch 的 syscall 钩子手法）
- **dmesg 静默**：驱动日志标签中性化 + info 级全静默
- **应用伪装**：一键切换「系统更新」外观 / 隐藏桌面图标

### 🌐 寄生控制台（浏览器管理）
- 管理器内启动本地服务（`127.0.0.1:38214`，密钥在路径中鉴权）
- 手机浏览器打开即得完整管理台：**状态 / 超级用户 / 模块 / 日志 / 关于**
- 超级用户页：真实应用图标 + 点击滑块授权 root（内核级 profile）
- 模块管理：刷入 zip / 启停 / 执行 action.sh / 卸载
- 管理器后台存活期间可用，被杀自动恢复（打开一次 App 即重新激活）

### 🧩 模块生态
- 完整支持 KernelSU 模块格式（Magic Mount）
- 模块图标 / action.sh / webroot 均原生支持

---

## 📲 安装

1. 在 [Releases](../../releases) 下载对应架构的管理器 APK 并安装
2. 管理器 → 安装 → 修补 boot 镜像（自动内嵌对应 KMI 的内核模块）→ 刷入 → 重启
3. 打开管理器，看到「已 root」即完成

> 首次开启「隐藏内核名字」时会提示安装 SELinux 修复模块（放行内核读取配置），点确认后重启生效。

---

## 🌐 浏览器控制台

1. 管理器 → 设置 → 寄生工作台（开启）
2. 复制完整地址（含访问密钥），在浏览器打开
3. 支持「添加到主屏幕」，获得独立入口的 PWA

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
| QQ 群友 | happy / 我的错 / 晚雾渡星河 / 沐神 / 爱吃肉的棒男孩 / 青凤 |

---

## 📄 许可证

[GPL-3.0](LICENSE) —— HydraSU 为自由软件，基于 KernelSU-Next 修改，遵循同等开源协议。
