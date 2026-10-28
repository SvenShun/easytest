package com.testhub.uiexec.service;

import com.testhub.common.web.ApiException;
import com.testhub.uiexec.artifact.ArtifactStore;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;

/** Pixel-diff two screenshots and expose baseline/current/diff artifacts. */
@Service
public class ImageDiffService {

    private final ArtifactStore artifacts;

    public ImageDiffService(ArtifactStore artifacts) {
        this.artifacts = artifacts;
    }

    public DiffResult compare(byte[] baselineBytes, byte[] currentBytes, double threshold) {
        if (baselineBytes == null || baselineBytes.length == 0 || currentBytes == null || currentBytes.length == 0) {
            throw ApiException.badRequest("对比需要基准图和当前图");
        }
        try {
            BufferedImage baseline = ImageIO.read(new ByteArrayInputStream(baselineBytes));
            BufferedImage current = ImageIO.read(new ByteArrayInputStream(currentBytes));
            if (baseline == null || current == null) {
                throw ApiException.badRequest("无法解析图片（支持 PNG/JPEG）");
            }
            int width = Math.min(baseline.getWidth(), current.getWidth());
            int height = Math.min(baseline.getHeight(), current.getHeight());
            BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            long changed = 0;
            int tolerance = (int) Math.max(0, Math.min(threshold <= 0 ? 16 : threshold, 255));
            long total = (long) width * height;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int a = baseline.getRGB(x, y);
                    int b = current.getRGB(x, y);
                    int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
                    int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
                    int delta = Math.max(Math.abs(ar - br), Math.max(Math.abs(ag - bg), Math.abs(ab - bb)));
                    if (delta > tolerance) {
                        changed++;
                        diff.setRGB(x, y, 0xffff3b30);
                    } else {
                        int gray = (ar + ag + ab) / 3;
                        diff.setRGB(x, y, 0x22ffffff | (gray << 16) | (gray << 8) | gray);
                    }
                }
            }
            double mismatch = total == 0 ? 0 : Math.round(changed * 10000.0 / total) / 100.0;
            String prefix = "diff-" + Instant.now().toEpochMilli();
            String baselineUrl = artifacts.publicUrl(artifacts.put("screen-diff", prefix + "-baseline.png", baselineBytes));
            String currentUrl = artifacts.publicUrl(artifacts.put("screen-diff", prefix + "-current.png", currentBytes));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(diff, "png", buffer);
            String diffUrl = artifacts.publicUrl(artifacts.put("screen-diff", prefix + "-diff.png", buffer.toByteArray()));
            return new DiffResult(width, height, baseline.getWidth(), baseline.getHeight(),
                    current.getWidth(), current.getHeight(), mismatch, diffUrl, baselineUrl, currentUrl);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("截图对比失败: " + e.getMessage());
        }
    }

    public record DiffResult(int width, int height, int baselineWidth, int baselineHeight,
                             int currentWidth, int currentHeight, double mismatchPercent,
                             String diffUrl, String baselineUrl, String currentUrl) {
    }
}
