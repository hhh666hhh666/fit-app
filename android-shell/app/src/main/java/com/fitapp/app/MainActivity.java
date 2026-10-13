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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 壳应用：一个 WebView 加载线上站点（见 res/values/strings.xml 的 app_url）。
 *
 * 取舍（与 quizzy 壳同一路线）：不嵌 H5 产物、直接加载线上地址——
 * 网站更新时 App 无需重发；代价是首次打开需要网络（应用本身也依赖联网）。
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

    /** WebView 的文件选择回调：结果回来前必须一直持有，且只能回传一次。 */
    private ValueCallback<Uri[]> filePathCallback;

    /** 走「拍照」时的输出地址；相册结果不经过它（用于区分二者判断）。 */
    private Uri cameraOutputUri;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // localStorage：登录 token 等存这里，必须开
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 站点内的跳转留在 App 内，不抛去系统浏览器
        webView.setWebViewClient(new WebViewClient());

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
            webView.loadUrl(getString(R.string.app_url));
        } else {
            webView.restoreState(savedInstanceState);
        }
        setContentView(webView);
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
