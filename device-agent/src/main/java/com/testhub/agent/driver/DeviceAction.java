package com.testhub.agent.driver;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Device action request, matching contract §3:
 *
 * <pre>
 * {"action":"click","target":{"text":"登录"}}
 * {"action":"input","target":{"text":"用户名"},"text":"tester"}
 * {"action":"swipe","from":{"x":500,"y":1500},"to":{"x":500,"y":400},"durationMs":300}
 * {"action":"back"} {"action":"home"} {"action":"launch","app":"com.testhub.demo"} {"action":"wait","ms":500}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceAction {

    private String action;
    private Target target;
    private String text;
    private Point from;
    private Point to;
    private Integer durationMs;
    private Integer ms;
    private String app;
    private java.util.List<Finger> fingers;

    public DeviceAction() {
    }

    public DeviceAction(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    @JsonAlias("type")
    public void setAction(String action) {
        this.action = action;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Point getFrom() {
        return from;
    }

    public void setFrom(Point from) {
        this.from = from;
    }

    public Point getTo() {
        return to;
    }

    public void setTo(Point to) {
        this.to = to;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getMs() {
        return ms;
    }

    public void setMs(Integer ms) {
        this.ms = ms;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public java.util.List<Finger> getFingers() { return fingers; }
    public void setFingers(java.util.List<Finger> fingers) { this.fingers = fingers; }

    // Convenience factories used by tests and internal callers.

    public static DeviceAction click(String text) {
        DeviceAction action = new DeviceAction("click");
        action.setTarget(new Target(null, text, null));
        return action;
    }

    public static DeviceAction clickById(String id) {
        DeviceAction action = new DeviceAction("click");
        action.setTarget(new Target(id, null, null));
        return action;
    }

    public static DeviceAction input(String targetText, String value) {
        DeviceAction action = new DeviceAction("input");
        action.setTarget(new Target(null, targetText, null));
        action.setText(value);
        return action;
    }

    public static DeviceAction back() {
        return new DeviceAction("back");
    }

    public static DeviceAction home() {
        return new DeviceAction("home");
    }

    /** Action target: match by id first, then by text; optional 0-based editable index. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Target {

        private String id;
        private String text;
        private Integer index;

        public Target() {
        }

        public Target(String id, String text, Integer index) {
            this.id = id;
            this.text = text;
            this.index = index;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public boolean isBlank() {
            return (id == null || id.isBlank()) && (text == null || text.isBlank()) && index == null;
        }

        public String describe() {
            if (text != null && !text.isBlank()) {
                return text;
            }
            if (id != null && !id.isBlank()) {
                return id;
            }
            if (index != null) {
                return "index " + index;
            }
            return "<empty>";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Finger {
        private Point from;
        private Point to;
        private Integer durationMs;

        public Finger() {
        }

        public Finger(Point from, Point to, Integer durationMs) {
            this.from = from;
            this.to = to;
            this.durationMs = durationMs;
        }

        public Point getFrom() { return from; }
        public void setFrom(Point from) { this.from = from; }
        public Point getTo() { return to; }
        public void setTo(Point to) { this.to = to; }
        public Integer getDurationMs() { return durationMs; }
        public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Point {

        private int x;
        private int y;

        public Point() {
        }

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }
}
