package com.testhub.uiexec.service;

import com.testhub.uiexec.artifact.ArtifactStore;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageDiffServiceTest {

    private final ArtifactStore artifacts = mock(ArtifactStore.class);
    private final ImageDiffService service = new ImageDiffService(artifacts);

    public ImageDiffServiceTest() {
        when(artifacts.put(anyString(), anyString(), any(byte[].class))).thenReturn("path");
        when(artifacts.publicUrl(anyString())).thenReturn("http://artifacts/path");
    }

    @Test
    void identicalScreenshotsHaveZeroMismatch() throws Exception {
        byte[] png = png(0xffffff);
        ImageDiffService.DiffResult result = service.compare(png, png, 16);
        assertThat(result.mismatchPercent()).isZero();
        assertThat(result.diffUrl()).isNotBlank();
    }

    @Test
    void differentScreenshotsReportMismatch() throws Exception {
        ImageDiffService.DiffResult result = service.compare(png(0xffffff), png(0x000000), 16);
        assertThat(result.mismatchPercent()).isEqualTo(100.0);
        assertThat(result.width()).isEqualTo(10);
        assertThat(result.height()).isEqualTo(10);
    }

    private byte[] png(int rgb) throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
