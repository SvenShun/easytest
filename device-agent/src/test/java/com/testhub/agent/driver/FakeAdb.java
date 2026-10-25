package com.testhub.agent.driver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Creates a small fake `adb` executable for hermetic driver tests. */
final class FakeAdb {

    private FakeAdb() {
    }

    static Path create(Path dir) throws Exception {
        Path log = dir.resolve("adb.log");
        Path devices = dir.resolve("devices.txt");
        Path xml = dir.resolve("window.xml");
        Path script = dir.resolve("adb");
        Files.writeString(devices, """
                List of devices attached
                fake-1 device product:test model:Pixel_7 device:test transport_id:1
                """);
        Files.writeString(xml, """
                <hierarchy rotation="0">
                  <node class="android.widget.FrameLayout" package="com.demo" bounds="[0,0][200,400]">
                    <node class="android.widget.Button" text="登录" resource-id="com.demo:id/login"
                          clickable="true" bounds="[10,20][110,80]"/>
                  </node>
                </hierarchy>
                """);
        String content = """
                #!/bin/bash
                echo "$@" >> %s
                if [ "$1" = "devices" ]; then cat %s; exit 0; fi
                if [ "$1" = "-s" ]; then shift 2; fi
                case "$1 $2" in
                  "exec-out screencap") printf '\\x89PNG\\r\\n\\x1a\\n'; exit 0 ;;
                  "shell uiautomator") exit 0 ;;
                  "exec-out cat") cat %s; exit 0 ;;
                  "install -r") exit 0 ;;
                  "install "*) exit 0 ;;
                esac
                exit 0
                """.formatted(log, devices, xml);
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        return script;
    }

    static Path log(Path dir) {
        return dir.resolve("adb.log");
    }

    static Path devices(Path dir) {
        return dir.resolve("devices.txt");
    }
}
