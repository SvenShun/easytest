package com.testhub.uiexec.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed view over {@code GET /device/{serial}/ui-tree}. The agent emits
 * {@code {width,height,screen:{app,title,texts,elements:[...]}}} (with a top-level elements alias);
 * this record tolerates either shape and missing fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceUiTree(Integer width, Integer height, Screen screen, List<Element> elements) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Screen(String app, String title, List<String> texts, List<Element> elements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Element(String id, String text, String type, Boolean clickable, Boolean editable,
                          Bounds bounds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bounds(Integer x, Integer y, Integer width, Integer height) {

        public boolean contains(int px, int py) {
            return x != null && y != null && width != null && height != null
                    && px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    public List<Element> allElements() {
        List<Element> result = new ArrayList<>();
        if (elements != null) {
            result.addAll(elements);
        }
        if (screen != null && screen.elements() != null) {
            result.addAll(screen.elements());
        }
        return result;
    }

    public List<String> allTexts() {
        List<String> result = new ArrayList<>();
        if (screen != null && screen.texts() != null) {
            result.addAll(screen.texts());
        }
        for (Element element : allElements()) {
            if (element != null && element.text() != null) {
                result.add(element.text());
            }
        }
        return result;
    }
}
