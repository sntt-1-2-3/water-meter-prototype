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
        assertThat(result.candidates()).as("Real cropped photo OCR: %s", result.error()).containsExactly("70.812");
        assertThat(result.region()).isNotNull();
        // No hard-coded photo-specific answer is present in the production engine.
    }

    @Test
    @EnabledIfSystemProperty(named = "photo.ocr.native", matches = "true")
    void locatesAndReadsWholePhotoAndTranslatedPhotoWithoutFixedCoordinates() throws Exception {
        BufferedImage photo = ImageIO.read(Path.of("..", "docs", "examples", "water-meter-aka.jpg").toFile());
        for (double scale : new double[]{1.0, .8}) {
            int width = (int) Math.round(photo.getWidth() * scale), height = (int) Math.round(photo.getHeight() * scale);
            BufferedImage variant = new BufferedImage(width + 180, height + 120, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = variant.createGraphics();
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, variant.getWidth(), variant.getHeight());
            graphics.drawImage(photo, 100, 50, width, height, null); graphics.dispose();
            var result = new PhotoOcrEngine().recognize(variant);
            System.out.println("PHOTO_WHOLE_SCALE_" + scale + "=" + result.candidates() + "; " + result.error());
            assertThat(result.candidates()).as("Whole photo OCR scale %s: %s; %s", scale, result.error(), result.rawText())
                    .containsExactly("70.812");
            assertThat(result.region()).isNotNull();
            assertThat(result.error()).isNull();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "photo.ocr.native", matches = "true")
    void recognizesAnotherReadingAndTwoRedDecimalPlaces() {
        BufferedImage face = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = face.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 800, 500);
        graphics.setFont(new Font("Monospaced", Font.BOLD, 58));
        String digits = "0012345";
        for (int i = 0; i < digits.length(); i++) {
            graphics.setColor(i < 5 ? Color.BLACK : Color.RED);
            graphics.drawString(digits.substring(i, i + 1), 170 + i * 43, 270);
        }
        graphics.setColor(Color.BLACK); graphics.setFont(new Font("Monospaced", Font.PLAIN, 20));
        graphics.drawString("MODEL 131.68", 150, 350); graphics.dispose();
        var result = new PhotoOcrEngine().recognize(face);
        System.out.println("PHOTO_OTHER_READING=" + result.candidates() + "; " + result.error());
        assertThat(result.candidates()).as("Different digits: %s; %s", result.rawText(), result.error())
                .containsExactly("123.45");
    }

    @Test
    @EnabledIfSystemProperty(named = "photo.ocr.native", matches = "true")
    void tiltedUncertainPhotographNeverFallsBackToSerialNumbers() throws Exception {
        BufferedImage photo = ImageIO.read(Path.of("..", "docs", "examples", "water-meter-tilted.jpg").toFile());
        var result = new PhotoOcrEngine().recognize(photo);
        System.out.println("PHOTO_TILTED_RESULT=" + result.candidates() + "; " + result.error());
        assertThat(result.candidates()).isEmpty();
        assertThat(result.error()).isNotBlank();
    }

    @Test
    @EnabledIfSystemProperty(named = "photo.ocr.native", matches = "true")
    void modelAndSerialNumbersOnAnUnlocatedFaceAreNeverSuggestedAsReadings() {
        BufferedImage face = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = face.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 800, 800);
        graphics.setColor(Color.BLACK); graphics.setFont(new Font("Monospaced", Font.BOLD, 44));
        graphics.drawString("MODEL 131.68", 60, 350); graphics.drawString("SN 99138606", 60, 430);
        graphics.dispose();
        var result = new PhotoOcrEngine().recognize(face);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.error()).contains("定位");
    }
}
