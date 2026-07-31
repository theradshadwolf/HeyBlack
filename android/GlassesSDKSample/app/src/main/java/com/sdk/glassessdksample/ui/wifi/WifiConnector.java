package com.glasssutdio.wear.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.permissions.Permission;

import java.io.IOException;
import java.net.InetAddress;

public final class WifiConnector {
    private static final String TAG = "WifiConnector";
    private static final long TIMEOUT_DURATION_MS = 15000L;

    public static final Companion INSTANCE = new Companion();

    private final Context context;
    private final WifiManager wifiManager;
    private final ConnectivityManager connectivityManager;
    @Nullable
    private ConnectivityManager.NetworkCallback networkCallback;

    public interface WifiConnectCallback {
        void onFailure(@NonNull String errorMessage);

        void onSuccess(@Nullable Network network);
    }

    public WifiConnector(@NonNull Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.connectivityManager =
                (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (this.wifiManager == null || this.connectivityManager == null) {
            throw new IllegalStateException("Wi-Fi services are unavailable");
        }
    }

    public void connectToWifi(@NonNull String ssid, @NonNull String password, @NonNull WifiConnectCallback callback) {
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> callback.onFailure("连接超时");
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_DURATION_MS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWithNewApi(ssid, password, callback, timeoutHandler, timeoutRunnable);
        } else {
            handleLegacyConnection(ssid, password, callback, timeoutHandler, timeoutRunnable);
        }
    }

    private void connectWithNewApi(
            @NonNull String ssid,
            @NonNull String password,
            @NonNull WifiConnectCallback callback,
            @NonNull Handler timeoutHandler,
            @NonNull Runnable timeoutRunnable) {
        WifiNetworkSpecifier specifier =
                new WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(password).build();
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build();

        networkCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        connectivityManager.bindProcessToNetwork(network);
                        callback.onSuccess(network);
                    }

                    @Override
                    public void onUnavailable() {
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        callback.onFailure("网络不可用");
                    }
                };

        connectivityManager.requestNetwork(request, networkCallback);
    }

    private void handleLegacyConnection(
            @NonNull String ssid,
            @NonNull String password,
            @NonNull WifiConnectCallback callback,
            @NonNull Handler timeoutHandler,
            @NonNull Runnable timeoutRunnable) {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
            new Handler(Looper.getMainLooper())
                    .postDelayed(() -> connectWithLegacyApi(ssid, password, callback, timeoutHandler, timeoutRunnable), 1000L);
            return;
        }
        connectWithLegacyApi(ssid, password, callback, timeoutHandler, timeoutRunnable);
    }

    private void connectWithLegacyApi(
            @NonNull String ssid,
            @NonNull String password,
            @NonNull WifiConnectCallback callback,
            @NonNull Handler timeoutHandler,
            @NonNull Runnable timeoutRunnable) {
        WifiConfiguration config = createWifiConfig(ssid, password);
        int networkId = wifiManager.addNetwork(config);
        if (networkId == -1) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            callback.onFailure("添加网络失败");
            return;
        }

        boolean connected = wifiManager.enableNetwork(networkId, true) && wifiManager.reconnect();
        timeoutHandler.removeCallbacks(timeoutRunnable);
        if (connected) {
            callback.onSuccess(null);
        } else {
            callback.onFailure("启用网络失败");
        }
    }

    private WifiConfiguration createWifiConfig(@NonNull String ssid, @NonNull String password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = '"' + ssid + '"';
        config.preSharedKey = '"' + password + '"';
        config.status = WifiConfiguration.Status.ENABLED;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        return config;
    }

    public boolean isNetworkValid() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public void disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.bindProcessToNetwork(null);
        }
        if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
        wifiManager.disconnect();
    }

    @NonNull
    public String getNetworkDebugInfo() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return "无活动网络";
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return "无网络能力信息";
        }
        return "已验证: " + capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                + ", VPN: " + capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    private boolean isConnectedToWifi(@NonNull Context context) {
        WifiManager manager =
                (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager == null) {
            return false;
        }
        WifiInfo info = manager.getConnectionInfo();
        return info != null && info.getNetworkId() != -1;
    }

    @Nullable
    private String getCurrentSSID(@NonNull Context context) {
        WifiManager manager =
                (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager == null || manager.getConnectionInfo() == null) {
            return null;
        }
        return manager.getConnectionInfo().getSSID();
    }

    @NonNull
    private String getDeviceIpAddress(@NonNull Context context) {
        WifiManager manager =
                (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager == null || manager.getConnectionInfo() == null) {
            return "0.0.0.0";
        }
        return Formatter.formatIpAddress(manager.getConnectionInfo().getIpAddress());
    }

    private boolean isIpAddressValid(@NonNull Context context) {
        String ipAddress = getDeviceIpAddress(context);
        Log.i(TAG, ipAddress);
        return !"0.0.0.0".equals(ipAddress);
    }

    private boolean isInternetAvailable() {
        try {
            return InetAddress.getByName("8.8.8.8").isReachable(2000);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isDnsWorking() {
        try {
            return InetAddress.getByName("www.google.com").isReachable(2000);
        } catch (IOException ignored) {
            return false;
        }
    }

    @NonNull
    public String checkNetworkStatus(@NonNull Context context) {
        if (!isConnectedToWifi(context)) {
            return "设备未连接到 Wi-Fi 网络";
        }
        if (!isIpAddressValid(context)) {
            return "设备未获得有效的 IP 地址";
        }
        if (!isInternetAvailable()) {
            return "设备无法访问互联网";
        }
        if (!isDnsWorking()) {
            return "DNS 配置错误，无法解析域名";
        }
        return "网络连接正常，能够访问互联网";
    }

    public static final class Companion {
        private Companion() {}

        @NonNull
        public String[] requiredPermissions() {
            return new String[] {
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.CHANGE_WIFI_STATE",
                Permission.ACCESS_FINE_LOCATION
            };
        }
    }
}
