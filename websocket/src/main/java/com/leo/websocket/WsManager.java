package com.leo.websocket;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.locks.ReentrantLock;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WsManager implements IWsManager {
    private static final String TAG = "WsManager";
    private static final ReentrantLock LOCK = new ReentrantLock();
    private final Handler wsMainHandler = new Handler(Looper.getMainLooper());

    private Request mRequest;
    private WebSocket mWebSocket;
    /**
     * websocket连接状态
     */
    private int mCurrentStatus = WsStatus.DISCONNECTED;
    /**
     * 是否为手动关闭websocket连接
     */
    private boolean isManualClose = false;

    private final Builder builder;

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            Log.e(TAG, "服务器重连接中...");
            buildConnect();
        }
    };

    private WsManager(Builder builder) {
        this.builder = builder;
    }

    @Override
    public WebSocket getWebSocket() {
        return mWebSocket;
    }

    @Override
    public void startConnect() {
        isManualClose = false;
        buildConnect();
    }

    @Override
    public void stopConnect() {
        isManualClose = true;
        disconnect();
    }

    @Override
    public synchronized boolean isWsConnected() {
        return mCurrentStatus == WsStatus.CONNECTED;
    }

    @Override
    public synchronized int getCurrentStatus() {
        return mCurrentStatus;
    }

    @Override
    public synchronized void setCurrentStatus(int currentStatus) {
        this.mCurrentStatus = currentStatus;
    }

    @Override
    public boolean sendMessage(String msg) {
        return send(msg);
    }

    @Override
    public boolean sendMessage(ByteString byteString) {
        return send(byteString);
    }

    private final WebSocketListener mWebSocketListener = new WebSocketListener() {

        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            // 服务器连接成功
            Log.i(TAG, "WebSocketListener:onOpen success.");
            mWebSocket = webSocket;
            setCurrentStatus(WsStatus.CONNECTED);
            cancelReconnect();
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
            // 接收到消息
            if (builder.messageReceiver != null) {
                builder.messageReceiver.onReceiver(webSocket, bytes);
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            // 接收到消息
            if (builder.messageReceiver != null) {
                builder.messageReceiver.onReceiver(webSocket, text);
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            // 服务器关闭中

        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            // 服务器连接已关闭
            Log.e(TAG, "WebSocketListener:onClosed;isManualClose" + isManualClose);
            if (!isManualClose) {
                try {
                    tryReconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
            Log.i(TAG, "WebSocketListener:onFailure.");
            try {
                tryReconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private boolean send(Object msg) {
        boolean isSend = false;
        if (mWebSocket != null && mCurrentStatus == WsStatus.CONNECTED) {
            if (msg instanceof String) {
                isSend = mWebSocket.send((String) msg);
            } else if (msg instanceof ByteString) {
                isSend = mWebSocket.send((ByteString) msg);
            }
            // 发送消息失败，尝试重连
            if (!isSend) {
                tryReconnect();
            }
        }
        return isSend;
    }

    private void disconnect() {
        if (mCurrentStatus == WsStatus.DISCONNECTED) {
            return;
        }
        cancelReconnect();
        if (builder.client != null) {
            builder.client.dispatcher().cancelAll();
        }
        if (mWebSocket != null) {
            boolean isClosed = mWebSocket.close(WsStatus.CODE.NORMAL_CLOSE, WsStatus.TIP.NORMAL_CLOSE);
            // 非正常关闭连接
            if (!isClosed) {
                Log.e(TAG, "disconnect:failed");
            }
        }
        setCurrentStatus(WsStatus.DISCONNECTED);
    }

    private synchronized void buildConnect() {
        if (!isNetworkConnected(builder.mContext)) {
            setCurrentStatus(WsStatus.DISCONNECTED);
            return;
        }
        switch (getCurrentStatus()) {
            case WsStatus.CONNECTED:
            case WsStatus.CONNECTING:
                break;
            default:
                setCurrentStatus(WsStatus.CONNECTING);
                initWebSocket();
                break;
        }
    }

    private void initWebSocket() {
        if (builder.client == null) {
            builder.client = new OkHttpClient.Builder()
                    .retryOnConnectionFailure(true)
                    .build();
        }
        if (mRequest == null) {
            mRequest = new Request.Builder()
                    .url(builder.url)
                    .build();
        }
        builder.client.dispatcher().cancelAll();
        try {
            LOCK.lockInterruptibly();
            try {
                builder.client.newWebSocket(mRequest, mWebSocketListener);
            } finally {
                LOCK.unlock();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void tryReconnect() {
        if (!builder.needReconnect || isManualClose) {
            return;
        }
        if (!isNetworkConnected(builder.mContext)) {
            setCurrentStatus(WsStatus.DISCONNECTED);
            wsMainHandler.postDelayed(reconnectRunnable, 10000);
            return;
        }
        setCurrentStatus(WsStatus.RECONNECT);
        wsMainHandler.postDelayed(reconnectRunnable, 10000);
    }

    /**
     * 检查网络是否连接
     *
     * @param context
     * @return
     */
    @SuppressLint("MissingPermission")
    private boolean isNetworkConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }

    private void cancelReconnect() {
        wsMainHandler.removeCallbacks(reconnectRunnable);
    }

    public interface IMessageReceiver {
        void onReceiver(@NonNull WebSocket webSocket, @NonNull ByteString byteString);

        void onReceiver(@NonNull WebSocket webSocket, @NonNull String string);
    }

    public static final class Builder {
        private Context mContext;
        private OkHttpClient client;
        private String url;
        private boolean needReconnect = true;
        private IMessageReceiver messageReceiver;

        public Builder setContext(Context mContext) {
            this.mContext = mContext;
            return this;
        }

        public Builder setClient(OkHttpClient okHttpClient) {
            this.client = okHttpClient;
            return this;
        }

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setNeedReconnect(boolean needReconnect) {
            this.needReconnect = needReconnect;
            return this;
        }

        public Builder setMessageReceiver(IMessageReceiver messageReceiver) {
            this.messageReceiver = messageReceiver;
            return this;
        }

        public WsManager build() throws Throwable {
            if (mContext == null || client == null) {
                throw new Throwable("mContext or client is null.");
            }
            if (TextUtils.isEmpty(url)) {
                throw new Throwable("url is null or empty.");
            }
            return new WsManager(this);
        }
    }
}
