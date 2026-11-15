# EasyTest Demo App

最小 Android 登录界面，供真机/模拟器 UI 自动化验收：

- 用户名输入框（hint「用户名」）
- 密码输入框（hint「密码」）
- 「登录」按钮
- `tester` / `secret` →「登录成功」

构建（需要 Android SDK 的 `android.jar` 与 build-tools）：

```bash
ANDROID_JAR=/path/to/android.jar BUILD_TOOLS=/path/to/build-tools ./scripts/build-demo-apk.sh
```

APK 不提交到仓库（避免把旧包名二进制带入新命名）。先构建，再安装：

```bash
./scripts/build-demo-apk.sh
adb install -r examples/android-demo/easytest-demo.apk
```

真机 device-agent：

```bash
EASYTEST_AGENT_MODE=adb \
EASYTEST_AGENT_ADB=/path/to/adb \
EASYTEST_AGENT_APP_PACKAGE=com.easytest.demo \
./scripts/mvn.sh -q -pl device-agent spring-boot:run
```
