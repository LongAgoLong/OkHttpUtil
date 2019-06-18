package com.leo.okhttputil;

import android.app.Application;

import com.leo.okhttplib.dns.TimeoutDNS;
import com.leo.okhttplib.interceptor.RetryIntercepter;
import com.leo.okhttplib.ssl.HttpSSLUtils;
import com.leo.okhttplib.ssl.SSLParams;
import com.zhy.http.okhttp.OkHttpUtils;

import java.net.Proxy;
import java.util.ArrayList;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

public class LeoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ArrayList<ConnectionSpec> unSafeConnectionSpecs = HttpSSLUtils.getUnSafeConnectionSpecs();
        SSLParams sslParams = HttpSSLUtils.getSslSocketFactory(null, null, null);
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager)
                .connectionSpecs(unSafeConnectionSpecs)
                .dns(new TimeoutDNS(3000))
                .retryOnConnectionFailure(true)
                .addInterceptor(new RetryIntercepter(2))
                .proxy(Proxy.NO_PROXY)
                //其他配置
                .build();
        OkHttpUtils.initClient(okHttpClient);
    }
}
