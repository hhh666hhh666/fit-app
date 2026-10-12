package com.fitapp.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 壳应用：一个 WebView 加载线上站点（见 res/values/strings.xml 的 app_url）。
 *
 * 取舍（与 quizzy 壳同一路线）：不嵌 H5 产物、直接加载线上地址——
 * 网站更新时 App 无需重发；代价是首次打开需要网络（应用本身也依赖联网）。
 * H5 是 hash 路由（src/manifest.json 的 h5.router.mode），
 * 所以返回键的 canGoBack 语义天然贴合「页面内后退、到根页退出」。
 */
public class MainActivity extends Activity {

    private WebView webView;

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

        if (savedInstanceState == null) {
            webView.loadUrl(getString(R.string.app_url));
        } else {
            webView.restoreState(savedInstanceState);
        }
        setContentView(webView);
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
