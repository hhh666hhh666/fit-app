package com.fitapp.app;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;

/**
 * 给 H5 用的壳能力，挂在 {@code window.FitShell}（0.1.6 起）。
 *
 * <p>目前只有震动：会话页完成一组时轻震一下（{@code frontend/src/utils/haptics.ts}）。
 * 不直接靠 H5 的 {@code navigator.vibrate}：它在 WebView 里能不能震取决于系统版本与厂商，
 * 自己调 {@link Vibrator} 才可靠；H5 那边先找这个桥，找不到（旧壳、浏览器）才退回 navigator.vibrate。</p>
 *
 * <p>⚠️ 这里的方法任何加载进来的页面都能调，所以只放无害的能力（震一下），别往里加读写数据的口子。</p>
 */
public class ShellBridge {

    /** 单次最长震多久（毫秒）：防止 H5 传错值把手机一直震下去 */
    private static final long MAX_MS = 200;

    private final Vibrator vibrator;

    ShellBridge(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * 按「震、停、震…」的毫秒序列震动，用逗号分隔，如 {@code "12"}、{@code "12,80,28"}。
     * 解析失败或设备没有马达就什么也不做。
     */
    @JavascriptInterface
    public void vibrate(String pattern) {
        if (vibrator == null || !vibrator.hasVibrator() || pattern == null) return;
        try {
            String[] parts = pattern.split(",");
            long[] timings = new long[parts.length + 1];
            timings[0] = 0; // 立刻开始
            for (int i = 0; i < parts.length; i++) {
                timings[i + 1] = Math.max(0, Math.min(MAX_MS, Long.parseLong(parts[i].trim())));
            }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1));
        } catch (RuntimeException ignored) {
            // 传错格式、系统拦掉：不震就是了，不能影响记录本身
        }
    }
}
