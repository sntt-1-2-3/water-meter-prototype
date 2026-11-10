package com.zhishuiyun.water.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class MeterDigitLocatorTest {
    @Test
    void overRangeRowIsRejectedInsteadOfDroppingLeadingDigits() {
        var face = new java.awt.image.BufferedImage(850, 500, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = face.createGraphics();
        graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 850, 500);
        graphics.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 58));
        String digits = "0012345678901";
        for (int i = 0; i < digits.length(); i++) {
            graphics.setColor(i < 10 ? java.awt.Color.BLACK : java.awt.Color.RED);
            graphics.drawString(digits.substring(i, i + 1), 100 + i * 43, 270);
        }
        graphics.dispose();
        assertThat(MeterDigitLocator.locate(face)).isNull();
    }

    @Test
    void locatesActualRollerRowWithoutUsingPictureCoordinates() throws Exception {
        var image = ImageIO.read(Path.of("..", "docs", "examples", "water-meter-aka.jpg").toFile());
        var located = MeterDigitLocator.locate(image);
        assertThat(located).isNotNull();
        System.out.println("LOCATED_WINDOW=" + located);
        assertThat(located.digits()).hasSize(8);
        assertThat(located.decimalPlaces()).isEqualTo(3);
        assertThat(located.bounds().getCenterY()).isBetween(640.0, 715.0);
    }

    @Test
    @EnabledIfSystemProperty(named = "photo.ocr.native", matches = "true")
    void readsEachLocatedDigitUsingActualNativeOcr() throws Exception {
        var image = ImageIO.read(Path.of("..", "docs", "examples", "water-meter-aka.jpg").toFile());
        var located = MeterDigitLocator.locate(image);
        assertThat(located).isNotNull();
        Tesseract engine = new Tesseract();
        engine.setDatapath(LoadLibs.extractTessResources("tessdata").getAbsolutePath());
        engine.setLanguage("eng"); engine.setOcrEngineMode(1); engine.setPageSegMode(8);
        engine.setVariable("tessedit_char_whitelist", "0123456789");
        engine.setVariable("user_defined_dpi", "300");
        engine.setVariable("load_system_dawg", "0"); engine.setVariable("load_freq_dawg", "0");
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < located.digits().size(); i++) {
            var prepared = MeterDigitLocator.digitImage(image, located.digits().get(i), 120);
            ImageIO.write(prepared, "png", Path.of("target", "located-digit-" + i + ".png").toFile());
            String text = engine.doOCR(prepared).trim();
            System.out.println("LOCATED_DIGIT_" + i + "=" + text);
            digits.append(text);
        }
        assertThat(digits.toString()).isEqualTo("00070812");
    }
}
