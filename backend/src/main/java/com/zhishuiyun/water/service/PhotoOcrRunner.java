package com.zhishuiyun.water.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Bounds request latency even when a native OCR call ignores Java interruption. */
@Component
public class PhotoOcrRunner {
    private final PhotoOcrEngine engine;
    private final Duration timeout;
    // No queue: one stuck native call cannot accumulate photos, tasks or more native threads.
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "photo-ocr-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    public PhotoOcrRunner(PhotoOcrEngine engine, @Value("${photo.ocr.timeout-seconds:20}") int seconds) {
        this(engine, Duration.ofSeconds(Math.max(1, Math.min(45, seconds))));
    }

    PhotoOcrRunner(PhotoOcrEngine engine, Duration timeout) {
        this.engine = engine;
        this.timeout = timeout;
    }

    public PhotoOcrEngine.OcrResult recognize(BufferedImage image) {
        Future<PhotoOcrEngine.OcrResult> job;
        try { job = worker.submit(() -> engine.recognize(image)); }
        catch (RejectedExecutionException error) {
            return failure("本地识别引擎正忙，请稍后重试；照片已保留，也可核对原图后人工填写");
        }
        try { return job.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException error) {
            job.cancel(true);
            return failure("本次识别超时，已停止等待；照片已保留，请框选数字区域后重试或人工填写");
        } catch (InterruptedException error) {
            job.cancel(true);
            Thread.currentThread().interrupt();
            return failure("识别等待已中断，照片已保留，请稍后重试或人工填写");
        } catch (ExecutionException error) {
            if (error.getCause() instanceof VirtualMachineError fatal) throw fatal;
            if (error.getCause() instanceof ThreadDeath fatal) throw fatal;
            return failure("本地识别引擎暂不可用，照片已保留，请核对原图后人工填写");
        }
    }

    private PhotoOcrEngine.OcrResult failure(String message) {
        return new PhotoOcrEngine.OcrResult("", List.of(), null, "local OCR", message);
    }

    @PreDestroy
    public void close() { worker.shutdownNow(); }
}
