package com.testhub.agent.driver;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

/**
 * Renders a simulated screen into a 720x1280 PNG so the AI Runtime and the web
 * console have something visually meaningful to show (offline demo).
 */
@Component
public class ScreenRenderer {

    public static final int WIDTH = 720;
    public static final int HEIGHT = 1280;

    private static final Color BACKGROUND = new Color(0x0F, 0x17, 0x2A);
    private static final Color STATUS_BAR = new Color(0x11, 0x18, 0x27);
    private static final Color TITLE_COLOR = new Color(0xF8, 0xFA, 0xFC);
    private static final Color TEXT_COLOR = new Color(0xCB, 0xD5, 0xE1);
    private static final Color MUTED_COLOR = new Color(0x94, 0xA3, 0xB8);
    private static final Color BUTTON_FILL = new Color(0x25, 0x63, 0xEB);
    private static final Color BUTTON_BORDER = new Color(0x60, 0xA5, 0xFA);
    private static final Color INPUT_FILL = new Color(0x1E, 0x29, 0x3B);
    private static final Color INPUT_BORDER = new Color(0x47, 0x55, 0x69);
    private static final Color ACCENT = new Color(0x38, 0xBD, 0xF8);

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public byte[] render(UiTree tree) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g.setColor(BACKGROUND);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(STATUS_BAR);
            g.fillRect(0, 0, WIDTH, 48);

            Screen screen = tree.screen();
            String app = screen.app() == null ? "" : screen.app();
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            g.setColor(MUTED_COLOR);
            g.drawString(app, 24, 31);
            g.setColor(ACCENT);
            g.fillOval(WIDTH - 44, 18, 14, 14);

            g.setColor(TITLE_COLOR);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
            drawCentered(g, screen.title() == null ? "" : screen.title(), 170);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
            g.setColor(TEXT_COLOR);
            List<String> texts = screen.texts() == null ? List.of() : screen.texts();
            int textY = 260;
            for (String text : texts) {
                if (text != null && !text.isBlank()) {
                    drawCentered(g, text, textY);
                }
                textY += 52;
            }

            if (screen.elements() != null) {
                for (UiElement element : screen.elements()) {
                    drawElement(g, element);
                }
            }

            g.setColor(new Color(0x1F, 0x29, 0x37));
            g.fillRect(0, HEIGHT - 72, WIDTH, 72);
            g.setColor(MUTED_COLOR);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            g.drawString("TestHub simulated device", 24, HEIGHT - 32);
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to render screenshot", e);
        }
    }

    private void drawElement(Graphics2D g, UiElement element) {
        if (element == null || element.bounds() == null) {
            return;
        }
        Bounds bounds = element.bounds();
        boolean button = "button".equalsIgnoreCase(element.type());
        boolean editable = element.editable() || "editable".equalsIgnoreCase(element.type());

        g.setStroke(new BasicStroke(2f));
        if (button) {
            g.setColor(BUTTON_FILL);
            g.fillRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 20, 20);
            g.setColor(BUTTON_BORDER);
            g.drawRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 20, 20);
        } else if (editable) {
            g.setColor(INPUT_FILL);
            g.fillRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 14, 14);
            g.setColor(INPUT_BORDER);
            g.drawRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 14, 14);
        } else {
            return;
        }

        String label = element.text() == null ? "" : element.text();
        int fontSize = button ? 30 : 26;
        g.setFont(new Font(Font.SANS_SERIF, button ? Font.BOLD : Font.PLAIN, fontSize));
        FontMetrics metrics = g.getFontMetrics();
        int x;
        int y;
        if (button) {
            x = bounds.centerX() - metrics.stringWidth(label) / 2;
            y = bounds.centerY() + metrics.getAscent() / 2 - 2;
        } else {
            x = bounds.x() + 24;
            y = bounds.centerY() + metrics.getAscent() / 2 - 2;
        }
        g.setColor(button ? Color.WHITE : MUTED_COLOR);
        g.drawString(label, x, y);
    }

    private void drawCentered(Graphics2D g, String text, int baselineY) {
        FontMetrics metrics = g.getFontMetrics();
        int x = (WIDTH - metrics.stringWidth(text)) / 2;
        g.drawString(text, Math.max(24, x), baselineY);
    }
}
