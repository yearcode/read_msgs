# read_msgs

把手机上新收到的短信，**每 1 分钟**自动通过邮箱（SMTP）转发到指定邮箱的 Android 工具。

- 支持 Android 10（API 29）及以上
- 兼容 Gmail / QQ 邮箱 / 163 邮箱 / 任意支持 SMTP 的邮箱
- 支持 SMTP 纯文本 / SSL / TLS(STARTTLS) 三种加密方式

> ⚠️ 仅用于转发你自己的短信。请在符合当地法律法规的前提下使用，尊重隐私。

## 功能

- 前台服务每 1 分钟轮询一次短信收件箱
- 只转发开启服务**之后**新收到的短信（不会把历史短信全部倒出）
- 一次把 1 分钟内收到的多条短信合并为一封邮件，包含：发件人、时间、内容
- **发送成功才会推进读取进度**：发送失败时下个周期自动重试，不丢短信
- 开机自启（厂商系统可能限制，被系统杀掉后打开 App 点一次“保存并启动”即可）
- App 内可配置：发件邮箱、收件邮箱（支持多个）、SMTP 服务器、端口、加密方式、账号/授权码
- 可一键“测试 SMTP 发送”

## 工作原理

```
[短信收件箱] --(每60秒 ContentResolver 增量查询)--> [新短信列表]
        --(合并成邮件, javax.mail SMTP 发送)--> [指定邮箱]
```

- 使用**前台服务 + 常驻通知**实现 1 分钟粒度轮询。
  Android 8+ 之后后台无法自由定时执行；WorkManager 最短周期 15 分钟，
  无法满足 1 分钟一次，因此必须使用前台服务（通知栏会常驻一条“运行中”通知）。
- 使用 JavaMail 的 Android 移植版（`com.sun.mail:android-mail`）走 SMTP 发信。

## 权限说明

| 权限 | 用途 |
|---|---|
| `READ_SMS` | 读取短信收件箱（敏感权限，首次启动运行时弹窗授权） |
| `INTERNET` | 连接 SMTP 服务器发邮件 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | 运行前台轮询服务 |
| `POST_NOTIFICATIONS` | Android 13+ 显示常驻服务通知 |
| `RECEIVE_BOOT_COMPLETED` | 开机后自动拉起服务 |

> 说明：`READ_SMS` 属于受限权限。本应用不面向应用商店分发，
> 通过 APK 直接安装（旁加载）时，系统会照常弹出运行时授权框，可以正常使用。
> Google Play 会拒绝包含此类权限的应用，本应用仅用于个人/自有设备。

## 邮箱配置示例

### Gmail（需开启两步验证 + 应用专用密码）

1. 浏览器登录 Google 账号 → 安全 → 开启两步验证
2. 安全 → 应用专用密码 → 生成一个 16 位密码
3. 本 App 配置：
   - 发件邮箱 / 账号：`你的账号@gmail.com`
   - 密码：16 位应用专用密码（不是登录密码）
   - SMTP：`smtp.gmail.com`，端口 `465`，加密方式 `SSL`
     （或端口 `587`，加密方式 `TLS/STARTTLS`）

### QQ 邮箱

- 设置 → 账户 → 开启 SMTP，生成**授权码**
- SMTP：`smtp.qq.com`，端口 `465` SSL 或 `587` TLS，密码填授权码

### 163 邮箱

- 设置 → POP3/SMTP/IMAP → 开启 SMTP，获取客户端授权密码
- SMTP：`smtp.163.com`，端口 `465` SSL 或 `587` TLS，密码填授权密码

## 编译方法

### 方法一：直接用 GitHub Actions 的产物（不需要本地环境）

1. 打开本仓库的 **Actions** 页 → 选择 **build-apk** 工作流
2. 点 **Run workflow**（或任意一次 push 后自动触发）
3. 运行结束后，在本次运行页面底部的 **Artifacts** 里下载
   `read_msgs-debug-apk`，解压得到 `app-debug.apk` 即可安装

### 方法二：Android Studio

1. Android Studio（建议 Ladybug 及以上，内置 JDK 17）→ Open 本目录
2. 首次打开等待 Gradle 同步下载依赖（需要网络，依赖从 Google Maven / Maven Central 拉取）
3. 菜单 Build → Build App Bundle(s) / APK(s) → Build APK(s)
4. 产物在 `app/build/outputs/apk/debug/app-debug.apk`

> 仓库没有提交 Gradle Wrapper，用 Android Studio 或命令行前，可在项目根执行一次
> `gradle wrapper --gradle-version 8.9`（需本机已装 Gradle 8.9+），或直接使用
> 已安装的 Gradle：`gradle assembleDebug`。

### 命令行编译（本机已装 JDK17 + Android SDK）

```bash
# 设置 SDK 路径（macOS/Linux）
export ANDROID_HOME=$HOME/Library/Android/sdk        # macOS 示例
# export ANDROID_HOME=$HOME/Android/Sdk              # Linux 示例
gradle assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 安装与使用

1. 允许安装未知来源应用，安装 `app-debug.apk`
2. 打开 App，填写邮箱配置，点“测试 SMTP 发送”确认可用
3. 点“保存并启动转发”，授予“短信”权限
4. 通知栏出现常驻通知即表示运行中
5. 收到新短信后最多 1 分钟内会收到转发邮件

## 常见问题

- **通知栏没有常驻通知/收不到转发**：把 App 加入厂商后台白名单（设置 → 电池/后台管理 → 允许自启动/后台运行）；重启手机后若未自动启动，手动打开一次 App。
- **测试发送失败 AuthenticationFailedException**：密码/授权码错误，或 Gmail 未开启应用专用密码。
- **SSL 连接失败**：换端口与加密方式（465+SSL / 587+TLS 二选一）。
- **担心错过边界**：只有“发送成功”才会推进进度；进程被杀期间的新短信会在重启服务后于下一分钟补发。

## 免责声明

本项目仅供学习与个人合法用途。请勿用于非法监控或侵犯他人隐私。
