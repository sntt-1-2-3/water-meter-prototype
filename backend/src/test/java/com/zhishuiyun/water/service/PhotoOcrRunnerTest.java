package com.zhishuiyun.water.service;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhotoOcrRunnerTest {
    private final BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);

    @Test
    void returnsCompletedResultWithoutInventingAReading() {
        PhotoOcrEngine engine = mock(PhotoOcrEngine.class);
        var expected = new PhotoOcrEngine.OcrResult("70.812", List.of("70.812"), 80.0, "TEST", null);
        when(engine.recognize(any())).thenReturn(expected);
        PhotoOcrRunner runner = new PhotoOcrRunner(engine, Duration.ofSeconds(2));
        try { assertThat(runner.recognize(image)).isSameAs(expected); }
        finally { runner.close(); }
    }

    @Test
    void uninterruptibleNativeWorkTimesOutAndCannotQueueMorePhotos() throws Exception {
        PhotoOcrEngine engine = mock(PhotoOcrEngine.class);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        when(engine.recognize(any())).thenAnswer(call -> {
            try {
                while (release.getCount() > 0) {
                    try { release.await(); }
                    catch (InterruptedException ignored) { /* Simulate native code ignoring Java interruption. */ }
                }
                return new PhotoOcrEngine.OcrResult("999", List.of("999"), 99.0, "LATE", null);
            } finally { finished.countDown(); }
        });
        PhotoOcrRunner runner = new PhotoOcrRunner(engine, Duration.ofMillis(150));
        try {
            long start = System.nanoTime();
            var timedOut = runner.recognize(image);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(timedOut.error()).contains("超时");
            assertThat(timedOut.candidates()).isEmpty();
            var busy = runner.recognize(image);
            assertThat(busy.error()).contains("正忙");
            assertThat(busy.candidates()).isEmpty();
            verify(engine, times(1)).recognize(any());
        } finally {
            release.countDown();
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            runner.close();
        }
    }

    @Test
    void engineFailureReturnsSafeManualFallback() {
        PhotoOcrEngine engine = mock(PhotoOcrEngine.class);
        when(engine.recognize(any())).thenThrow(new IllegalStateException("private-native-path"));
        PhotoOcrRunner runner = new PhotoOcrRunner(engine, Duration.ofSeconds(2));
        try {
            var result = runner.recognize(image);
            assertThat(result.error()).contains("人工填写").doesNotContain("private-native-path");
            assertThat(result.candidates()).isEmpty();
        } finally { runner.close(); }
    }
}
