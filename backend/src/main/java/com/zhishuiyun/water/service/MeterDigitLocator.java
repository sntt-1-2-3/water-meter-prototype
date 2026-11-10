package com.zhishuiyun.water.service;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative locator for aligned black integer rollers followed by 2–3 red fractional rollers. */
final class MeterDigitLocator {
    record Digit(Rectangle bounds, boolean red) {}
    record Window(Rectangle bounds, List<Digit> digits, int decimalPlaces) {}
    private record Part(Rectangle bounds, int pixels) {
        double cx() { return bounds.getCenterX(); }
        double cy() { return bounds.getCenterY(); }
    }

    static Window locate(BufferedImage original) {
        double scale = Math.min(1, 1800.0 / Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { graphics.drawImage(original, 0, 0, width, height, null); }
        finally { graphics.dispose(); }
        int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
        List<Part> reds = components(rgb, width, height, true);
        if (reds.size() < 2) return null;
        List<Part> blacks = components(rgb, width, height, false);
        Window best = null;
        for (Part anchor : reds) {
            List<Part> row = reds.stream().filter(part -> {
                double ratio = part.bounds.height / (double) anchor.bounds.height;
                return ratio >= .7 && ratio <= 1.4 && Math.abs(part.cy() - anchor.cy()) <= anchor.bounds.height * .3;
            }).sorted(Comparator.comparingDouble(Part::cx)).toList();
            if (row.size() < 2 || row.size() > 3) continue;
            double pitch = row.size() == 2 ? row.get(1).cx() - row.get(0).cx()
                    : (row.get(2).cx() - row.get(0).cx()) / 2;
            double digitHeight = row.stream().mapToInt(part -> part.bounds.height).average().orElse(0);
            double centerY = row.stream().mapToDouble(Part::cy).average().orElse(0);
            if (pitch < digitHeight * .55 || pitch > digitHeight * 1.8) continue;
            boolean regular = true;
            for (int n = 1; n < row.size(); n++)
                if (Math.abs(row.get(n).cx() - row.get(n - 1).cx() - pitch) > pitch * .25) regular = false;
            if (!regular) continue;
            List<Part> integerDigits = new ArrayList<>();
            double expectedX = row.get(0).cx() - pitch;
            // Inspect one extra place so an over-range row is rejected rather than truncated.
            for (int slot = 0; slot < 10; slot++) {
                Part closest = null;
                double distance = pitch * .28;
                for (Part part : blacks) {
                    double ratio = part.bounds.height / digitHeight;
                    double delta = Math.abs(part.cx() - expectedX);
                    if (ratio >= .72 && ratio <= 1.22 && Math.abs(part.cy() - centerY) <= digitHeight * .35
                            && delta < distance && !integerDigits.contains(part)) {
                        closest = part; distance = delta;
                    }
                }
                if (closest == null) break;
                integerDigits.add(0, closest);
                expectedX = closest.cx() - pitch;
            }
            // Three aligned black rollers plus two red rollers are the minimum required evidence.
            if (integerDigits.size() < 3 || integerDigits.size() > 9) continue;
            List<Digit> digits = new ArrayList<>();
            for (Part part : integerDigits) digits.add(new Digit(mapped(part.bounds, scale, original), false));
            for (Part part : row) digits.add(new Digit(mapped(part.bounds, scale, original), true));
            Rectangle bounds = new Rectangle(digits.get(0).bounds);
            for (Digit digit : digits) bounds.add(digit.bounds);
            Window found = new Window(bounds, List.copyOf(digits), row.size());
            if (best == null || found.digits.size() > best.digits.size()) best = found;
        }
        return best;
    }

    private static Rectangle mapped(Rectangle rect, double scale, BufferedImage original) {
        int x = Math.max(0, (int) Math.floor(rect.x / scale));
        int y = Math.max(0, (int) Math.floor(rect.y / scale));
        int right = Math.min(original.getWidth(), (int) Math.ceil((rect.x + rect.width) / scale));
        int bottom = Math.min(original.getHeight(), (int) Math.ceil((rect.y + rect.height) / scale));
        return new Rectangle(x, y, right - x, bottom - y);
    }

    static boolean red(int rgb) {
        int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
        return r > 110 && r - g > 35 && r > g * 1.45 && r > b * 1.35;
    }

    static boolean ink(int rgb, boolean red, int threshold) {
        if (red) return red(rgb);
        int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
        return !red(rgb) && (r * 30 + g * 59 + b * 11) / 100 < threshold;
    }

    private static List<Part> components(int[] rgb, int width, int height, boolean red) {
        byte[] mask = new byte[rgb.length];
        for (int i = 0; i < rgb.length; i++) if (ink(rgb[i], red, 95)) mask[i] = 1;
        int[] queue = new int[rgb.length];
        List<Part> result = new ArrayList<>();
        int minimumHeight = Math.max(8, height / 180);
        for (int start = 0; start < mask.length; start++) {
            if (mask[start] == 0) continue;
            int head = 0, tail = 1; queue[0] = start; mask[start] = 0;
            int left = start % width, right = left, top = start / width, bottom = top;
            while (head < tail) {
                int point = queue[head++], x = point % width, y = point / width;
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int neighbor = ny * width + nx;
                    if (mask[neighbor] != 0) { mask[neighbor] = 0; queue[tail++] = neighbor; }
                }
            }
            int w = right - left + 1, h = bottom - top + 1;
            if (h >= minimumHeight && h < height * (width >= height * 2L ? .98 : .22) && w >= 2 && w < h * 1.25
                    && w >= h * .08 && tail >= w * h * .08)
                result.add(new Part(new Rectangle(left, top, w, h), tail));
        }
        return result;
    }

    /** Isolates ink from each roller's background, separators and neighbouring partial digits. */
    static BufferedImage digitImage(BufferedImage source, Digit digit, int threshold) {
        Rectangle box = digit.bounds;
        int factor = Math.max(1, Math.min(4, 110 / box.height));
        int padding = 12;
        BufferedImage result = new BufferedImage(box.width * factor + padding * 2,
                box.height * factor + padding * 2, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = result.createGraphics();
        try { graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, result.getWidth(), result.getHeight()); }
        finally { graphics.dispose(); }
        for (int y = 0; y < box.height; y++) for (int x = 0; x < box.width; x++) {
            if (!ink(source.getRGB(box.x + x, box.y + y), digit.red, threshold)) continue;
            for (int dy = 0; dy < factor; dy++) for (int dx = 0; dx < factor; dx++)
                result.getRaster().setSample(padding + x * factor + dx, padding + y * factor + dy, 0, 0);
        }
        return result;
    }
}
