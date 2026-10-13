# Terminus

一个基于 [Termux](https://github.com/termux/termux-app) `terminal-view` 的 Android 终端应用，支持 root / Shizuku 提权与“脱离”（detach）会话：终端进程可在应用退出后继续运行，回到应用时可重新附着并恢复工作目录与环境变量。

## 功能

- 真实 PTY 终端：基于 Termux `TerminalView` / `TerminalSession`，支持 256 色、字体与光标样式自定义。
- 提权方式：root（`su`）或 Shizuku（ADB/root 授权），可创建以指定 uid/gid 运行的会话。
- 会话脱离：将交互式 shell 交给后台守护进程或 Shizuku 用户服务托管，应用进程退出后 shell 继续存活；重新进入时恢复 cwd 与 env。
- 额外按键栏：`Esc` `Tab` `Ctrl` `Alt` `Shift`、方向键、`Home`/`End`/`PgUp`/`PgDn`/`Ins`/`Del` 以及常用 shell 符号（`|` `-` `/` `~` `$` 等）。
- 快捷命令集合与模板：可保存常用命令与启动模板。
- 自动清理：启动时清理失效会话；可选的空闲 PTY 回收；按“无历史”或提权级别清理。

## 架构

- `TerminalActivity` + Termux `TerminalView`：前台终端。
- `termbridge`（`app/src/main/native/termbridge.c`）：native 守护进程，负责 PTY 分配、cwd/env 捕获、输出回放（`REPLAY_CAP`）与空闲自毁。
- `RemoteShellService`：Shizuku 用户服务，在 root/ADB 上下文中承载 PTY；`IShellService.aidl` 定义 IPC 契约。
- `ShizukuPty`：应用侧通过抽象本地 socket + 每会话随机 token 与用户服务通信。
- `SessionManager` / `Session` / `Template`：会话与模板持久化（`sessions.json` / `templates.json`，原子写入）。
- `SessionCleaner` / `TermSettings`：清理策略与设置。

### 关键约束

- **不要随意修改 Shizuku 用户服务的 `.version(3)`**。Shizuku 以 `(组件, version, tag)` 标识用户服务，变更 version 会重启用户服务并杀死已脱离的 PTY。仅在 `IShellService.aidl` 契约变化时才需要提升。

## 构建

需要 Android SDK、NDK 与 JDK 17。

```bash
# 编译 native 桥接（arm64-v8a / x86_64）
NDK="$ANDROID_NDK_HOME"; TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p app/src/main/jniLibs/arm64-v8a app/src/main/jniLibs/x86_64
"$TC/aarch64-linux-android24-clang" -O2 -fPIE -pie -o app/src/main/jniLibs/arm64-v8a/libtermbridge.so app/src/main/native/termbridge.c
"$TC/x86_64-linux-android24-clang" -O2 -fPIE -pie -o app/src/main/jniLibs/x86_64/libtermbridge.so app/src/main/native/termbridge.c

# 构建 APK
gradle assembleDebug

# 单元测试
gradle testDebugUnitTest
```

CI（`.github/workflows/build.yml`）会自动完成上述构建与测试并上传 `TerminusRoot-debug` 产物。

## 权限

- root：通过 `su`。
- Shizuku：需要在 Shizuku 应用中授权本应用。

## 许可

本项目使用 Termux 的 `terminal-view` / `terminal-emulator`（GPLv3）作为依赖，请遵循其许可条款。
