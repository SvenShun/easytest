package com.testhub.core.feature.ai;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic offline model. Produces a login scenario when the requirement
 * mentions 登录, a search scenario when it mentions 搜索, otherwise a generic
 * scenario titled after the first sentence.
 */
@Component
public class MockTextModelProvider implements TextModelProvider {

    private static final int MAX_TITLE_LENGTH = 80;

    @Override
    public boolean supports(String provider) {
        return "mock".equalsIgnoreCase(provider);
    }

    @Override
    public List<BddDraft> generate(TextModelInput input) {
        String text = input.requirementText() == null ? "" : input.requirementText();
        String firstSentence = firstSentence(text);
        if (text.contains("登录")) {
            return List.of(new BddDraft("用户使用正确凭据登录",
                    "已打开 App 且未登录",
                    "输入用户名 tester 和密码 secret 并点击登录",
                    "进入首页并显示 登录成功"));
        }
        if (text.contains("搜索")) {
            return List.of(new BddDraft(firstSentence,
                    "已打开 App 并处于首页",
                    "点击搜索框并输入关键字后提交搜索",
                    "展示与关键字匹配的搜索结果列表"));
        }
        return List.of(new BddDraft(firstSentence,
                "已打开 App 并处于可操作状态",
                "根据需求执行核心操作：" + firstSentence,
                "操作成功并展示预期结果"));
    }

    private String firstSentence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return "通用功能验证";
        }
        String sentence = trimmed.split("[。！？!?\\n\\r]")[0].trim();
        if (sentence.isEmpty()) {
            sentence = trimmed;
        }
        return sentence.length() > MAX_TITLE_LENGTH ? sentence.substring(0, MAX_TITLE_LENGTH) : sentence;
    }
}
