package com.fitapp.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 「双轨」里的**更新那一轨**：后台探测线上版本，有新版就悄悄下好、下次启动生效。
 *
 * <h3>为什么分两步（暂存 / 切换）</h3>
 * 下载后**不立刻替换** {@code filesDir/web}——那一刻 WebView 可能正在读里面的文件，
 * 换掉会让正在加载的页面炸掉。所以下载只解到 {@code web-staging}，
 * 真正搬进 {@code web} 放到**下次启动的最早期**（{@link #applyPending()}），那时还没有
 * 任何页面在读文件。切换靠 {@code renameTo}，同一分区内是原子操作，不会出现半个版本。
 *
 * <h3>协议</h3>
 * {@code <站点根>/web/version.json}：
 * <pre>{"version":"0.13.0","url":"/web/web-0.13.0.zip","sha256":"…"}</pre>
 * {@code url} 可为绝对地址也可为相对站点根的路径；{@code sha256} 为空则跳过校验
 * （只建议在调试期这么做）。清单由 {@code scripts/build-apk-web.sh} 生成。
 */
final class WebUpdater {

    private static final String TAG = "WebUpdater";
    private static final String PREFS = "fitapp_web";
    private static final String KEY_APPLIED = "applied_version";
    private static final String KEY_PENDING = "pending_version";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;

    private final Context app;

    WebUpdater(Context c) {
        this.app = c.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 把上次下好的暂存包切成正式目录。**必须在任何页面加载之前调用。**
     *
     * @return true 表示本次启动用的就是刚切过来的新版
     */
    boolean applyPending() {
        File staging = LocalWeb.stagingDir(app);
        if (!new File(staging, "index.html").isFile()) {
            // 没有待切换的包（首次启动或上次没更新）
            return false;
        }

        String pending = prefs().getString(KEY_PENDING, null);
        File target = LocalWeb.downloadedDir(app);
        File backup = LocalWeb.backupDir(app);
        try {
            deleteTree(backup);
            if (target.exists() && !target.renameTo(backup)) {
                // rename 失败多半是目录里有别的挂载或权限问题，退化成删除后重建
                deleteTree(target);
            }
            if (!staging.renameTo(target)) {
                throw new IOException("web-staging → web 重命名失败");
            }
            deleteTree(backup);

            SharedPreferences.Editor editor = prefs().edit().remove(KEY_PENDING);
            if (pending != null) {
                editor.putString(KEY_APPLIED, pending);
            }
            editor.apply();
            Log.i(TAG, "已切换到暂存产物：" + pending);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "切换暂存产物失败，继续用旧版", e);
            deleteTree(staging);
            return false;
        }
    }

    /** 后台检查更新，不阻塞启动、失败只记日志。 */
    void checkAsync(String siteRoot) {
        if (siteRoot == null || siteRoot.isEmpty()) {
            return;
        }
        // 独立线程：壳里没有线程池基础设施，为这一次网络往返开一个就够
        Thread t = new Thread(() -> {
            try {
                check(siteRoot);
            } catch (Exception e) {
                Log.i(TAG, "检查更新跳过：" + e.getMessage());
            }
        }, "web-update-check");
        t.setDaemon(true);
        t.start();
    }

    private void check(String siteRootRaw) throws Exception {
        String root = siteRootRaw.endsWith("/")
                ? siteRootRaw.substring(0, siteRootRaw.length() - 1)
                : siteRootRaw;

        JSONObject manifest = new JSONObject(fetchText(root + "/web/version.json"));
        String version = manifest.optString("version", "");
        String zipUrl = manifest.optString("url", "");
        String sha = manifest.optString("sha256", "");
        if (version.isEmpty() || zipUrl.isEmpty()) {
            return;
        }

        String applied = prefs().getString(KEY_APPLIED, null);
        String pending = prefs().getString(KEY_PENDING, null);
        if (version.equals(applied) || version.equals(pending)) {
            return;
        }

        String resolved = zipUrl.startsWith("http") ? zipUrl : root + "/" + stripLeadingSlash(zipUrl);
        File zip = new File(app.getCacheDir(), "web-" + version + ".zip");
        try {
            download(resolved, zip);
            if (!sha.isEmpty() && !sha.equalsIgnoreCase(sha256(zip))) {
                throw new IOException("sha256 不匹配");
            }

            File staging = LocalWeb.stagingDir(app);
            deleteTree(staging);
            if (!staging.mkdirs()) {
                throw new IOException("建暂存目录失败");
            }
            unzip(zip, staging);
            if (!new File(staging, "index.html").isFile()) {
                deleteTree(staging);
                throw new IOException("包内没有 index.html");
            }
            prefs().edit().putString(KEY_PENDING, version).apply();
            Log.i(TAG, "新产物已暂存：" + version + "，下次启动生效");
        } finally {
            // 下载失败的半成品不要留在缓存里
            if (zip.exists()) {
                //noinspection ResultOfMethodCallIgnored
                zip.delete();
            }
        }
    }

    private static String stripLeadingSlash(String s) {
        String r = s;
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        return r;
    }

    private static String fetchText(String url) throws IOException {
        HttpURLConnection conn = open(url);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("version.json HTTP " + code);
            }
            try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > MAX_MANIFEST_BYTES) {
                        throw new IOException("version.json 过大");
                    }
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void download(String url, File out) throws IOException {
        HttpURLConnection conn = open(url);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("下载产物 HTTP " + code);
            }
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                byte[] chunk = new byte[16 * 1024];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    os.write(chunk, 0, n);
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept", "application/json, application/zip, */*");
        return conn;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] chunk = new byte[16 * 1024];
            int n;
            while ((n = in.read(chunk)) > 0) {
                digest.update(chunk, 0, n);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * 解压到指定目录。**逐个 entry 校验落点**——压缩包里的 {@code ../} 能让文件写到目录之外
     * （Zip Slip），产物虽然是自己打的包，但校验的成本近乎为零。
     */
    private static void unzip(File zip, File root) throws IOException {
        String rootPath = root.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] chunk = new byte[16 * 1024];
            while ((entry = zin.getNextEntry()) != null) {
                File target = new File(root, entry.getName());
                if (!target.getCanonicalPath().startsWith(rootPath)) {
                    throw new IOException("包内路径越界：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!target.mkdirs() && !target.isDirectory()) {
                        throw new IOException("建目录失败：" + entry.getName());
                    }
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("建父目录失败：" + entry.getName());
                }
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(target))) {
                    int n;
                    while ((n = zin.read(chunk)) > 0) {
                        os.write(chunk, 0, n);
                    }
                }
            }
        }
    }

    static void deleteTree(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        target.delete();
    }
}
