package com.zhishuiyun.water.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class PhotoOcrEngineTest {
    @Test
    void decimalParsingIsBoundedAndNeverGuessesMissingPoint() {
        assertThat(PhotoOcrEngine.extractCandidates("00070812")).containsExactly("70812");
        assertThat(PhotoOcrEngine.extractCandidates("00070.812")).containsExactly("70.812");
        assertThat(PhotoOcrEngine.extractCandidates("70,812")).containsExactly("70.812");
        assertThat(PhotoOcrEngine.extractCandidates("123456789012 1.2345 modelX70 999999999.999"))
                .containsExactly("999999999.999");
    }

    @Test
    void invalidImageCannotBeReportedAsSuccessfulOcr() {
        var result = new PhotoOcrEngine().recognize(null);
        assertThat(result.candidates()).isEmpty(); assertThat(result.error()).isNotBlank();
    }

    /** Explicit native verification: never silently skip an unavailable engine when this test is requested. */
    @Test
    @EnabledIfSystemProperty(named = "photo.ocr.native", matches = "true")
    void realNativeEngineReadsClearDigits() {
        BufferedImage input = new BufferedImage(900, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = input.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 900, 180);
        graphics.setColor(Color.BLACK); graphics.setFont(new Font("Monospaced", Font.BOLD, 100));
        graphics.drawString("00070.812", 40, 130); graphics.dispose();
        var result = new PhotoOcrEngine().recognize(input);
        assertThat(result.engine()).contains("Tesseract");
        assertThat(result.candidates()).as("Native OCR result: %s; %s", result.rawText(), result.error()).contains("70.812");
    }

    @Test
    @EnabledIfSystemProperty(named = "photo.ocr.native", matches = "true")
    void realPhotographProducesUntrustedCandidatesNotACannedAnswer() throws Exception {
        BufferedImage photo = ImageIO.read(Path.of("..", "docs", "examples", "water-meter-aka.jpg").toFile());
        ImageIO.write(photo.getSubimage(510, 640, 390, 75), "png", Path.of("target", "photo-ocr-crop-debug.png").toFile());
        var result = new PhotoOcrEngine().recognize(photo.getSubimage(510, 640, 390, 75));
        System.out.println("PHOTO_REAL_SAMPLE_CANDIDATES=" + result.candidates());
        System.out.println("PHOTO_REAL_SAMPLE_RAW=" + result.rawText().replace('\n', '|'));
        assertThat(result.candidates()).as("Real cropped photo OCR: %s", result.error()).contains("70");
        // No hard-coded photo-specific answer is present in the production engine.
    }
}
