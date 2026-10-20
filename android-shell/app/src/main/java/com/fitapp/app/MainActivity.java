package com.fitapp.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 壳应用：WebView 加载 H5，走**双轨**——本地产物优先、线上地址兜底。
 *
 * <h3>双轨怎么跑（2026-10-20 起）</h3>
 * 1. 启动最早期先 {@link WebUpdater#applyPending()}，把上次下好的新版切成正式目录；
 * 2. 若本地有产物（下载版或 apk 内置的 {@code assets/web}），经
 *    {@link LocalWeb} 的虚拟域加载——**零网络等待**，这是消灭「点图标后白屏几秒」的关键；
 * 3. 两侧都没有产物时才回落线上地址（{@code app_url}），保证壳永远是能用的容器；
 * 4. 后台探测 {@code /web/version.json}，有新版就悄悄下好，**下次启动生效**。
 *
 * 这条路线替代了早先「只加载线上地址」的做法：原来的取舍是为了「网站更新 App 无需重发」，
 * 代价是每次冷启动都要等网络把 HTML 与 JS 拉完。现在两者都要——本地有产物所以快，
 * 后台自动更新所以仍然不用重发 apk。**例外**：若某次前端改动必须立刻在手机上看到，
 * 仍以线上版为准（本地版要等下一次启动才换）。
 *
 * <h3>API 因此变成跨域</h3>
 * 页面 origin 是 {@code https://appassets.androidplatform.net}，接口在线上域名，
 * 后端必须放行该 origin（{@code FITAPP_CORS_ALLOWED_ORIGINS}）。漏配的表现是
 * 「页面能开、数据全是网络异常」。
 *
 * <h3>登录态会因此重置一次</h3>
 * localStorage 按 origin 隔离：换到虚拟域后，原先存在线上域名下的 token 读不到，
 * 升级后需要重新登录一次——**只此一次**，之后都在虚拟域里。
 *
 * H5 是 hash 路由（src/manifest.json 的 h5.router.mode），
 * 所以返回键的 canGoBack 语义天然贴合「页面内后退、到根页退出」。
 *
 * ⚠️ 文件选择器（2026-10-13）：H5 的 `uni.chooseImage` 底层是
 * 「创建一个隐藏的 <input type="file"> 再 click()」。**浏览器**会接管这个点击弹出系统选择器；
 * 而 WebView **只有实现 WebChromeClient.onShowFileChooser 才会接管**，默认实现直接返回 false
 * —— 点击被静默吞掉，表现就是「点『拍照记录这一餐』没反应」（手机浏览器正常、App 里没反应）。
 * 这里补上该回调，并提供「相册 ＋ 拍照」两个来源。
 */
public class MainActivity extends Activity {

    /** 文件选择器的请求码（相册与拍照共用一次 startActivityForResult）。 */
    private static final int REQ_FILE_CHOOSER = 1001;

    private WebView webView;

    /** 本地资源拦截器（虚拟域 → filesDir/web → assets/web）。 */
    private WebViewAssetLoader assetLoader;

    /** 后台更新器：只在 onCreate 里用来触发探测。 */
    private WebUpdater updater;

    /** WebView 的文件选择回调：结果回来前必须一直持有，且只能回传一次。 */
    private ValueCallback<Uri[]> filePathCallback;

    /** 走「拍照」时的输出地址；相册结果不经过它（用于区分二者判断）。 */
    private Uri cameraOutputUri;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updater = new WebUpdater(this);
        // 必须在任何页面加载之前：切换靠 rename，此时还没人读 filesDir/web
        updater.applyPending();

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // localStorage：登录 token 等存这里，必须开
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 本地产物 + 静态资源基本都是瞬时的，这一层只影响兜底时的线上加载
        webView.setBackgroundColor(0xFFFFFFFF);

        // 站点内的跳转留在 App 内，不抛去系统浏览器；虚拟域的请求交给 LocalWeb
        assetLoader = LocalWeb.loader(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // 非虚拟域（线上地址、API）返回 null ＝ 不拦截，交回网络
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        // 见类注释：不设这个，H5 里的 <input type="file"> 点击不会有任何反应
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                // 连续触发时先放弃上一个未完成的回调，避免回调泄漏
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                cameraOutputUri = null;

                Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
                gallery.addCategory(Intent.CATEGORY_OPENABLE);
                gallery.setType("image/*");

                Intent chooser = Intent.createChooser(gallery, "选择照片");
                // 「拍照」作为附加入口与相册并列出现在选择器里
                Intent camera = buildCameraIntent();
                if (camera != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                }
                startActivityForResult(chooser, REQ_FILE_CHOOSER);
                return true;
            }
        });

        if (savedInstanceState == null) {
            // 本地优先、线上兜底——这一行就是「白屏几秒」与「即开即用」的分界
            String localEntry = LocalWeb.entryUrl(this);
            webView.loadUrl(localEntry != null ? localEntry : getString(R.string.app_url));
            // 后台探测新版：下好了不立刻换，等下次启动（见 WebUpdater 的类注释）
            updater.checkAsync(siteRoot());
        } else {
            webView.restoreState(savedInstanceState);
        }
        setContentView(webView);
    }

    /**
     * 线上站点根（形如 {@code https://fit.11-2.cn}），由 {@code app_url} 推导。
     * 换环境只改 strings.xml 一处，版本清单地址与兜底地址都跟着走。
     */
    private String siteRoot() {
        try {
            Uri u = Uri.parse(getString(R.string.app_url));
            if (u.getScheme() == null || u.getHost() == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder(u.getScheme()).append("://").append(u.getHost());
            if (u.getPort() > 0) {
                sb.append(':').append(u.getPort());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 构造「拍照」意图：输出到应用私有目录，经 FileProvider 以 content:// 授权给相机应用，
     * 因此**无需申请相机/存储权限**（minSdk 26 起 FileProvider 可用）。
     * 设备无可用相机时返回 null —— 选择器里就只剩相册入口，不影响其它功能。
     */
    private Intent buildCameraIntent() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                return null;
            }
            String name = "shot_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date()) + ".jpg";
            File out = new File(dir, name);
            cameraOutputUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", out);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return intent;
        } catch (Exception e) {
            cameraOutputUri = null;
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER) {
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) {
                    // 相册/文件：结果 URI 在 data 里
                    results = new Uri[]{data.getData()};
                } else if (cameraOutputUri != null) {
                    // 拍照：结果不回 data，值就在既定的输出 URI 上
                    results = new Uri[]{cameraOutputUri};
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results); // null ＝ 用户取消
                filePathCallback = null;
            }
            cameraOutputUri = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        // 页面内可后退就后退（H5 是 hash 路由，WebView 会认），到根页再退出
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
