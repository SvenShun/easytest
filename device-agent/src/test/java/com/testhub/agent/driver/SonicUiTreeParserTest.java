package com.testhub.agent.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the mapping from Sonic/uiautomator page source to TestHub's UI tree contract. */
class SonicUiTreeParserTest {

    private static final String PAGE_SOURCE = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout"
                    package="com.testhub.demo" clickable="false" bounds="[0,0][1080,2400]">
                <node index="1" text="登录" resource-id="btn-login" class="android.widget.Button"
                      clickable="true" bounds="[100,1000][980,1120]" />
                <node index="2" text="用户名" resource-id="input-username" class="android.widget.EditText"
                      clickable="true" bounds="[100,600][980,720]" />
                <node index="3" text="欢迎使用 TestHub" resource-id="label-welcome" class="android.widget.TextView"
                      clickable="false" bounds="[100,200][900,280]" />
              </node>
            </hierarchy>
            """;

    @Test
    void mapsUiautomatorNodesToTestHubElements() {
        UiTree tree = SonicAndroidDeviceDriver.parseUiTree(PAGE_SOURCE, 1080, 2400);

        assertThat(tree.width()).isEqualTo(1080);
        assertThat(tree.height()).isEqualTo(2400);
        assertThat(tree.elements()).hasSize(4);
        assertThat(tree.elements()).anySatisfy(element -> {
            assertThat(element.id()).isEqualTo("btn-login");
            assertThat(element.text()).isEqualTo("登录");
            assertThat(element.type()).isEqualTo("button");
            assertThat(element.clickable()).isTrue();
            assertThat(element.bounds().x()).isEqualTo(100);
            assertThat(element.bounds().width()).isEqualTo(880);
        });
        assertThat(tree.elements()).anySatisfy(element -> {
            assertThat(element.text()).isEqualTo("用户名");
            assertThat(element.editable()).isTrue();
            assertThat(element.type()).isEqualTo("editable");
        });
        assertThat(tree.screen().texts()).contains("登录", "用户名", "欢迎使用 TestHub");
        // Top-level alias is populated for the Python runtime.
        assertThat(tree.elements()).isEqualTo(tree.screen().elements());
    }
}
