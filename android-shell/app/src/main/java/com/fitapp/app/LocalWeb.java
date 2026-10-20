package com.fitapp.app;

import android.content.Context;
import android.webkit.WebResourceResponse;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;

/**
 * 「双轨」里的**本地那一轨**：H5 产物既可能在 apk 内置的 {@code assets/web}，
 * 也可能是后来下载到 {@code filesDir/web} 的新版。查找顺序是**下载版优先、内置版兜底**。
 *
 * <h3>为什么用 WebViewAssetLoader 而不是 file://</h3>
 * 直接 {@code file:///android_asset/...} 加载会踩三个坑：产物里引用的是绝对路径
 * （{@code /assets/xxx.js}），在 file 协议下会解析到文件系统根；页面 origin 变成
 * {@code null}，同源策略与 localStorage 行为都跟线上不一致；也拿不到 Secure Context。
 * {@link WebViewAssetLoader} 把本地目录映射成一个**虚拟 https 域**
 * （{@value #HOST}），上面三个问题一起消失，前端产物的 {@code base} 保持 {@code /} 不用改。
 *
 * <h3>代价：API 变跨域</h3>
 * 页面 origin 从此是 {@code https://appassets.androidplatform.net}，而 API 仍在线上域名，
 * 两者不同源——后端必须用 {@code FITAPP_CORS_ALLOWED_ORIGINS} 放行本域，否则接口全挂。
 * 前端侧则靠 {@code VITE_API_BASE} 注入绝对地址（见 {@code frontend/src/api/base.ts}）。
 */
final class LocalWeb {

    /** 虚拟域：WebView 内部专用，不发真实 DNS。 */
    static final String HOST = "appassets.androidplatform.net";
    static final String ORIGIN = "https://" + HOST;

    /** 入口页（hash 路由，形如 {ORIGIN}/index.html#/pages/home/index）。 */
    static final String ENTRY_PATH = "/index.html";

    /** apk 内置产物所在 assets 子目录。 */
    static final String ASSET_DIR = "web";

    private LocalWeb() {
    }

    static File downloadedDir(Context c) {
        return new File(c.getFilesDir(), "web");
    }

    static File stagingDir(Context c) {
        return new File(c.getFilesDir(), "web-staging");
    }

    static File backupDir(Context c) {
        return new File(c.getFilesDir(), "web-old");
    }

    static boolean hasDownloaded(Context c) {
        return new File(downloadedDir(c), "index.html").isFile();
    }

    /** apk 内置产物的版本号文件（由 `scripts/build-apk-web.sh` 写入，一行文本）。 */
    static final String VERSION_FILE = ".version";

    /**
     * 读 apk 内置产物的版本号；没有（未内置产物或旧包）返回 null。
     *
     * 作用只有一个：让壳知道「我肚子里这份是多少版」，从而在**首次启动**时不会把
     * 内容与内置版相同的线上包再下一遍。没有它的话 applied_version 初始为空，
     * 线上清单任何版本都被判成「比本地新」。
     */
    @Nullable
    static String bundledVersion(Context c) {
        InputStream in = null;
        try {
            in = c.getAssets().open(ASSET_DIR + "/" + VERSION_FILE);
            byte[] buf = new byte[128];
            int n = in.read(buf);
            if (n <= 0) {
                return null;
            }
            String v = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8).trim();
            return v.isEmpty() ? null : v;
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    static boolean hasBundled(Context c) {
        InputStream in = null;
        try {
            in = c.getAssets().open(ASSET_DIR + ENTRY_PATH);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * 可加载的本地入口；两侧都没有产物时返回 null，调用方回落到线上地址
     * （保持「壳是容器」这条底线：产物没进包也照样能用，只是慢）。
     */
    @Nullable
    static String entryUrl(Context c) {
        return (hasDownloaded(c) || hasBundled(c)) ? ORIGIN + ENTRY_PATH : null;
    }

    /** 装上虚拟域后，虚拟域下的请求全部走 {@link Handler}。 */
    static WebViewAssetLoader loader(Context c) {
        return new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/", new Handler(c))
                .build();
    }

    /** 分层查找：先 {@code filesDir/web}，再 {@code assets/web}。 */
    static final class Handler implements WebViewAssetLoader.PathHandler {

        private final Context app;

        Handler(Context c) {
            this.app = c.getApplicationContext();
        }

        @Nullable
        @Override
        public WebResourceResponse handle(String path) {
            String rel = normalize(path);
            if (rel == null) {
                return notFound();
            }

            File file = new File(downloadedDir(app), rel);
            if (file.isFile()) {
                try {
                    return respond(mimeOf(rel), new FileInputStream(file));
                } catch (IOException ignored) {
                    // 读不出来就继续试内置版——比直接 404 更接近用户预期
                }
            }

            try {
                return respond(mimeOf(rel), app.getAssets().open(ASSET_DIR + "/" + rel));
            } catch (IOException e) {
                return notFound();
            }
        }

        /** 去掉前导斜杠；空路径视作入口；含 {@code ..} 的一律拒绝（防目录穿越）。 */
        @Nullable
        private static String normalize(String path) {
            if (path == null) {
                return null;
            }
            String rel = path;
            while (rel.startsWith("/")) {
                rel = rel.substring(1);
            }
            if (rel.isEmpty()) {
                rel = "index.html";
            }
            return rel.contains("..") ? null : rel;
        }

        private static WebResourceResponse respond(String mime, InputStream in) {
            return new WebResourceResponse(mime, "UTF-8", in);
        }

        private static WebResourceResponse notFound() {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
        }
    }

    /**
     * 按扩展名给 MIME。**不能省**：WebView 对虚拟域的响应不做嗅探，
     * 类型给错会直接不执行脚本（表现为白屏但资源全是 200）。
     */
    static String mimeOf(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html") || p.endsWith(".htm")) {
            return "text/html";
        }
        if (p.endsWith(".js") || p.endsWith(".mjs")) {
            return "application/javascript";
        }
        if (p.endsWith(".css")) {
            return "text/css";
        }
        if (p.endsWith(".json") || p.endsWith(".map")) {
            return "application/json";
        }
        if (p.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (p.endsWith(".png")) {
            return "image/png";
        }
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (p.endsWith(".webp")) {
            return "image/webp";
        }
        if (p.endsWith(".gif")) {
            return "image/gif";
        }
        if (p.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (p.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (p.endsWith(".woff")) {
            return "font/woff";
        }
        if (p.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (p.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // 关不掉就算了，不值得让调用方处理
            }
        }
    }
}
