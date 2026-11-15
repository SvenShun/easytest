package com.easytest.agent.driver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebDeviceDriverTest {

    @Test
    void readsDomAndInteractsWithPage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = """
                    <html><head><title>Demo</title></head><body>
                    <h1>EasyTest Web</h1>
                    <input id="username" placeholder="username"/>
                    <button id="login">登录</button>
                    </body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            DeviceDescriptor descriptor = new DeviceDescriptor("web-1", "Web", "HtmlUnit", "web",
                    List.of("web"), true);
            WebDeviceDriver driver = new WebDeviceDriver(descriptor, url, new ScreenRenderer());
            UiTree tree = driver.uiTree();
            assertThat(tree.elements()).extracting(UiElement::text).contains("EasyTest Web", "登录");
            ActionResult clicked = driver.action(DeviceAction.click("登录"));
            assertThat(clicked.ok()).as(clicked.message()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
