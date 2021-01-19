package kg.delletenebre.serialmanager2.communication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import kg.delletenebre.serialmanager2.App;
import kg.delletenebre.serialmanager2.MainActivity;
import kg.delletenebre.serialmanager2.R;
import kg.delletenebre.serialmanager2.communication.impl.BluetoothCommunicator;
import kg.delletenebre.serialmanager2.communication.impl.SerialCommunicator;
import kg.delletenebre.serialmanager2.communication.impl.UsbCommunicator;
import kg.delletenebre.serialmanager2.communication.impl.WebServerCommunicator;
import kg.delletenebre.serialmanager2.utils.Utils;

public class CommunicationService extends Service implements SensorEventListener {
    private final static int NOTIFICATION_ID = 109;

    public final static String EXTRA_BLUETOOTH_ENABLED = "bluetooth_enabled";
    public final static String EXTRA_UPDATE_USB_CONNECTION = "update_usb";
    public final static String EXTRA_UPDATE_BLUETOOTH_CONNECTION = "update_bluetooth";

    private BroadcastReceiver mBroadcastReceiver;
    private BroadcastReceiver mLocalBroadcastReceiver;
    private LocalBroadcastManager mLocalBroadcastManager;
    private SharedPreferences mPrefs;

//    // **** USB **** //
//    private UsbConnection mUsbConnection;
//
//    // **** BLUETOOTH **** //
//    private BluetoothConnection mBluetoothConnection;
//
//    // **** WEB-SERVER **** //
//    private AsyncHttpServer mWebServer;
//    private List<WebSocket> mWebSockets;
//
//    // **** SERIAL **** //
//    private SerialPort mSerialPort;

    Map<CommunicatorType, BaseCommunicator> communicators;

//    SerialCommunicator serial;
//    UsbCommunicator usb;
//    BluetoothCommunicator bluetooth;
//    WebServerCommunicator web;

    private SensorManager mSensorManager;

    private Notification.Builder mNotificationBuilder;
    private RemoteViews mNotificationLayout;


    public CommunicationService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        App.log("CommunicationService-onStartCommand");

        for (BaseCommunicator communicator : communicators.values()) {
            communicator.openOrClose(
                    isCommunicationTypeEnabled(communicator.getCommunicatorType().getTypeCode()));
        }

//        if (intent != null) {
//            if (intent.getBooleanExtra(EXTRA_UPDATE_USB_CONNECTION, false)) {
//                mUsbConnection.findConnectedDevices();
//            }
//
//            if (intent.getBooleanExtra(EXTRA_BLUETOOTH_ENABLED, false)
//                    || intent.getBooleanExtra(EXTRA_UPDATE_BLUETOOTH_CONNECTION, false)) {
//                startBluetoothCommunication();
//            }
//        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        communicators = new HashMap<>();
        for (BaseCommunicator communicator : Arrays.asList(
                new SerialCommunicator(this),
                new UsbCommunicator(this),
                new BluetoothCommunicator(this),
                new WebServerCommunicator(this))) {
            communicators.put(communicator.getCommunicatorType(), communicator);
        }

        mPrefs = App.getInstance().getPrefs();
        initializeNotification();

//        mUsbConnection = new UsbConnection(this, App.getInstance().getIntPreference("usb_baud_rate"));
//        mBluetoothConnection = new BluetoothConnection();
//        if (App.getInstance().getBooleanPreference("bluetooth_adapter_turn_on")) {
//            mBluetoothConnection.enableAdapter();
//        }
//
//        mUsbConnection.findConnectedDevices();
//        startBluetoothCommunication();
//        startSerialCommunication();
//        startWebServer();

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action != null) {
                    switch (action) {
                        case UsbManager.ACTION_USB_DEVICE_DETACHED:
                            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                            ((UsbCommunicator) communicators.get(CommunicatorType.USB)).close(device);
//                            mUsbConnection.close(device.getDeviceName());
                            break;

                        case BluetoothAdapter.ACTION_STATE_CHANGED:
                            final int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                    BluetoothAdapter.ERROR);
                            if (BluetoothAdapter.STATE_TURNING_OFF == bluetoothState
                                    || BluetoothAdapter.ERROR == bluetoothState) {
                                communicators.get(CommunicatorType.BLUETOOTH).close();
//                                stopBluetoothCommunication();
                            }
                            break;

                        case App.ACTION_SEND_DATA:
                            Bundle extra = intent.getExtras();
                            if (extra != null && extra.containsKey("data")) {
                                String dataStr = String.valueOf(extra.get("data"));
                                String data = App.getInstance().compileFormulas(dataStr);

                                sendData(data);

                                if (extra.containsKey("id")) {
                                    sendActionComplete(String.valueOf(extra.get("id")));
                                }
                            }
                            break;
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(App.ACTION_SEND_DATA);
        intentFilter.addAction(App.ACTION_EXTERNAL_COMMAND);
        registerReceiver(mBroadcastReceiver, intentFilter);


        mLocalBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                App.log("CommunicationService-action" + action);
                if (action != null) {

                    switch (action) {
                        case App.LOCAL_ACTION_CONNECTION_ESTABLISHED:
                        case App.LOCAL_ACTION_CONNECTION_CLOSED:
                        case App.LOCAL_ACTION_CONNECTION_FAILED:
                            updateNotificationText();
                            break;

                        case App.LOCAL_ACTION_COMMAND_RECEIVED:
                            App.getInstance().detectCommand(intent.getStringExtra("command"));
                            break;

                        case App.LOCAL_ACTION_SEND_DATA:
                            sendData(intent.getStringExtra("data"));
                            break;

                        case App.LOCAL_ACTION_SETTINGS_UPDATED:
                            for (BaseCommunicator communicator : communicators.values()) {
                                communicator.openOrClose(false);
                            }
                            for (BaseCommunicator communicator : communicators.values()) {
                                communicator.openOrClose(
                                        isCommunicationTypeEnabled(communicator.getCommunicatorType().getTypeCode()));
                            }

//                            serial.openOrClose(isCommunicationTypeEnabled("serial"));
//                            usb.openOrClose(isCommunicationTypeEnabled("usb"));
//                            bluetooth.openOrClose(isCommunicationTypeEnabled("bluetooth"));
//                            web.openOrClose(isCommunicationTypeEnabled("web_socket"));

//                            if (isCommunicationTypeEnabled("usb")) {
//                                if (!mUsbConnection.hasOpened()) {
//                                    mUsbConnection.findConnectedDevices();
//                                }
//                            } else {
//                                mUsbConnection.closeAll();
//                            }
//
//                            if (isCommunicationTypeEnabled("bluetooth")) {
//                                if (mBluetoothConnection == null) {
//                                    startBluetoothCommunication();
//                                } else {
//                                    stopBluetoothCommunication();
//                                    startBluetoothCommunication();
//                                }
//                            } else {
//                                stopBluetoothCommunication();
//                            }
//
//
//                            if (isCommunicationTypeEnabled("web_socket")) {
//                                if (mWebServer == null) {
//                                    startWebServer();
//                                }
//                            } else {
//                                stopWebServer();
//                            }
//
//                            if (isCommunicationTypeEnabled("serial")) {
//                                if (mSerialPort == null) {
//                                    startSerialCommunication();
//                                }
//                            } else {
//                                stopSerialCommunication();
//                            }

                            updateNotificationText();
                            break;
                    }
                }
            }
        };
        IntentFilter localIntentFilter = new IntentFilter();
        localIntentFilter.addAction(App.LOCAL_ACTION_CONNECTION_ESTABLISHED);
        localIntentFilter.addAction(App.LOCAL_ACTION_CONNECTION_CLOSED);
        localIntentFilter.addAction(App.LOCAL_ACTION_CONNECTION_FAILED);
        localIntentFilter.addAction(App.LOCAL_ACTION_COMMAND_RECEIVED);
        localIntentFilter.addAction(App.LOCAL_ACTION_SETTINGS_UPDATED);
        localIntentFilter.addAction(App.LOCAL_ACTION_SEND_DATA);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mLocalBroadcastManager.registerReceiver(mLocalBroadcastReceiver, localIntentFilter);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor sensorLight = mSensorManager != null ? mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
        if (sensorLight != null) {
            mSensorManager.registerListener(this, sensorLight, SensorManager.SENSOR_DELAY_NORMAL);
        }

        sendBroadcast(new Intent(App.ACTION_SERVICE_STARTED));

        updateNotificationText();
    }

    @Override
    public void onDestroy() {
        App.log("CommunicationService - destroy");
//        if (isConnectionStateMessageEnabled()) {
//            sendData(App.ACTION_CONNECTION_LOST);
//        }

        for (BaseCommunicator communicator : communicators.values()) {
            communicator.openOrClose(false);
        }

//        serial.close();
//        usb.close();
//        bluetooth.close();
//        web.close();

//        mUsbConnection.closeAll();
//        mUsbConnection = null;
//        stopBluetoothCommunication();
//        stopWebServer();
//        stopSerialCommunication();

        mSensorManager.unregisterListener(this);
        mSensorManager = null;

        unregisterReceiver(mBroadcastReceiver);
        mBroadcastReceiver = null;

        sendBroadcast(new Intent(App.ACTION_SERVICE_STOPPED));

        mLocalBroadcastManager.unregisterReceiver(mLocalBroadcastReceiver);
        mLocalBroadcastManager = null;
        mLocalBroadcastReceiver = null;

        super.onDestroy();
    }

    public LocalBroadcastManager getLocalBroadcastManager() {
        return mLocalBroadcastManager;
    }

    private boolean isCommunicationTypeEnabled(String type) {
        return App.getInstance().getBooleanPreference(type + "_communication_enabled");
    }


    private int getNotificationTextColor(int style) {
        TextView tempTextView = new TextView(this);
        tempTextView = Utils.setTextAppearence(tempTextView, style);
        return tempTextView.getCurrentTextColor();
    }

    private void initializeNotification() {
        mNotificationLayout = new RemoteViews(getPackageName(), R.layout.layout_notification);

        int infoTextColor = getNotificationTextColor(
                R.style.TextAppearance_Compat_Notification_Info);

        int titleTextColor = getNotificationTextColor(
                R.style.TextAppearance_Compat_Notification_Title);

        Bitmap appIcon = getNotificationInfoIcon(R.drawable.notification_icon, infoTextColor);
        mNotificationLayout.setImageViewBitmap(R.id.app_icon, appIcon);

        for (BaseCommunicator communicator : communicators.values()) {
            Bitmap icon = getNotificationInfoIcon(communicator.getCommunicatorType().getIconId(), titleTextColor);
            mNotificationLayout.setImageViewBitmap(communicator.getCommunicatorType().getImageViewId(), icon);
        }


//
//        Bitmap usbIcon = getNotificationInfoIcon(R.drawable.ic_usb, textColor);
//        mNotificationLayout.setImageViewBitmap(R.id.usb_connections_icon, usbIcon);
//
//        Bitmap bluetoothIcon = getNotificationInfoIcon(R.drawable.ic_bluetooth_black_24dp, textColor);
//        mNotificationLayout.setImageViewBitmap(R.id.bluetooth_connections_icon, bluetoothIcon);
//
//        Bitmap websocketIcon = getNotificationInfoIcon(R.drawable.ic_language_black_24dp, textColor);
//        mNotificationLayout.setImageViewBitmap(R.id.web_socket_connections_icon, websocketIcon);
//
//        Bitmap serialIcon = getNotificationInfoIcon(R.drawable.ic_vga, textColor);
//        mNotificationLayout.setImageViewBitmap(R.id.serial_connections_icon, serialIcon);

        mNotificationBuilder = new Notification.Builder(this)
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.notification_icon)
                .setContent(mNotificationLayout)
                .setContentIntent(
                        PendingIntent.getActivity(this, 0,
                                new Intent(this, MainActivity.class), 0));

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    "serial.manager.v2", "notification_service", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
                mNotificationBuilder.setChannelId(notificationChannel.getId());
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 16) {
            startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
        } else {
            startForeground(NOTIFICATION_ID, mNotificationBuilder.getNotification());
        }
    }

    public void updateNotificationText() {
        App.log("CommunicationService-updateNotificationText");
//        if (isCommunicationTypeEnabled("usb")) {
//            e("mUsbConnection", "VISIBLE");
//            if (mNotificationLayout != null) {
//                mNotificationLayout.setTextViewText(R.id.usb_connections_count,
//                        String.valueOf(communicators.get(CommunicatorType.USB).getConnectionsCount()));
//                setNotificationInfoVisibility("usb", View.VISIBLE);
//            }
//        } else {
//            e("mUsbConnection", "GONE");
//            setNotificationInfoVisibility("usb", View.GONE);
//        }
//
//        if (isCommunicationTypeEnabled("bluetooth")) {
//            if (mNotificationLayout != null) {
//                int bluetoothIconId = R.drawable.ic_bluetooth_black_24dp;
////                if (mBluetoothConnection != null && mBluetoothConnection.isConnected()) {
//                bluetoothIconId = R.drawable.ic_bluetooth_connected_black_24dp;
////                }
//                int textColor = getNotificationTextColor(
//                        R.style.TextAppearance_Compat_Notification_Title);
//                Bitmap bluetoothIcon = getNotificationInfoIcon(bluetoothIconId, textColor);
//                mNotificationLayout.setImageViewBitmap(R.id.bluetooth_connections_icon, bluetoothIcon);
//            }
//            setNotificationInfoVisibility("bluetooth", View.VISIBLE);
//        } else {
//            setNotificationInfoVisibility("bluetooth", View.GONE);
//        }
//
//        if (isCommunicationTypeEnabled("web_socket")) {
//            if (mNotificationLayout != null) {
////                String webSocketCountText = "0";
////                if (mWebSockets != null) {
////                    webSocketCountText = String.valueOf(mWebSockets.size());
////                }
//                mNotificationLayout.setTextViewText(R.id.web_socket_connections_count,
//                        communicators.get(CommunicatorType.WEB).getConnectionsCount().toString());
//                setNotificationInfoVisibility("web_socket", View.VISIBLE);
//            }
//            mNotificationLayout.setTextViewText(R.id.ip_address,
//                    Utils.getIpAddress() + ":" + App.getInstance().getIntPreference("web_server_port",
//                            getString(R.string.pref_default_web_server_port)));
//            mNotificationLayout.setViewVisibility(R.id.ip_address, View.VISIBLE);
//        } else {
//            setNotificationInfoVisibility("web_socket", View.GONE);
//            mNotificationLayout.setViewVisibility(R.id.ip_address, View.GONE);
//        }
//
//        if (isCommunicationTypeEnabled("serial")) {
//            if (mNotificationLayout != null) {
////                mNotificationLayout.setTextViewText(R.id.usb_connections_count, String.valueOf(mUsbConnection.count()));
//                setNotificationInfoVisibility("serial", View.VISIBLE);
//            }
//        } else {
//            setNotificationInfoVisibility("serial", View.GONE);
//        }

        for (BaseCommunicator communicator : communicators.values()) {
            updateNotificationTextItem(communicator);
        }

        mNotificationBuilder.setContent(mNotificationLayout);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && mNotificationBuilder != null) {
            notificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
        }
    }

    private void setNotificationInfoVisibility(String type, int visibility) {
        mNotificationLayout.setViewVisibility(
                Utils.getIdIdentifier(this, type + "_connections_icon"), visibility);
        mNotificationLayout.setViewVisibility(
                Utils.getIdIdentifier(this, type + "_connections_count"), visibility);
    }

    private Bitmap getNotificationInfoIcon(int iconResId, int color) {
        Bitmap usbIcon = Utils.getBitmapFromVectorDrawable(this, iconResId);
        return Utils.tintBitmap(usbIcon, color);
    }


//    private void startBluetoothCommunication() {
//        if (isCommunicationTypeEnabled("bluetooth")) {
//            if (mBluetoothConnection != null) {
//                mBluetoothConnection.stop();
//                mBluetoothConnection = null;
//            }
//            mBluetoothConnection = new BluetoothConnection();
//            mBluetoothConnection.start(this);
//        }
//    }

//    private void stopBluetoothCommunication() {
//        if (mBluetoothConnection != null) {
//            mBluetoothConnection.stop();
//            mBluetoothConnection = null;
//
//            mLocalBroadcastManager.sendBroadcast(
//                    new Intent(App.LOCAL_ACTION_CONNECTION_CLOSED)
//                            .putExtra("type", "bluetooth")
//                            .putExtra("name", "bluetooth"));
//
//            updateNotificationText();
//        }
//    }

//    private void bluetoothSend(String message) {
//        if (mBluetoothConnection != null && mBluetoothConnection.isConnected()) {
//            mBluetoothConnection.write(message);
//        }
//    }

//    public void startWebServer() {
//        if (isCommunicationTypeEnabled("web_socket")) {
//            mWebServer = new AsyncHttpServer();
//            mWebSockets = new ArrayList<>();
//            final int port = App.getInstance().getIntPreference("web_server_port",
//                    getString(R.string.pref_default_web_server_port));
//            final String ipAddress = Utils.getIpAddress();
//
//            mWebServer.get("/", (request, response) -> {
//                String webSocketInfo = String.format(getString(R.string.web_socket_info),
//                        ipAddress, port);
//
//                response.send("<!DOCTYPE html><head><title>" + getString(R.string.app_name) + "</title><meta charset=\"utf-8\" /></head><body><h1>" + getString(R.string.app_name) + "</h1><i>version: <b>" + App.getInstance().getVersion() + "</b></i><br><br>" + webSocketInfo + "<br><br><a href=\"/test-websocket\">WebSocket test</a></body></html>");
//            });
//            mWebServer.get("/test-websocket", (request, response) -> {
//                AssetManager assetManager = getAssets();
//
//                String html = "<h1>404 Not found</h1>";
//                InputStream input;
//                try {
//                    input = assetManager.open("websoket_test.html");
//                    int size = input.available();
//                    byte[] buffer = new byte[size];
//                    //noinspection ResultOfMethodCallIgnored
//                    input.read(buffer);
//                    input.close();
//
//                    html = new String(buffer);
//                    html = html.replace("{{address}}", ipAddress + ":" + port + "/ws");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//                response.send(html);
//            });
//            mWebServer.listen(port);
//            mWebServer.websocket("/ws", (webSocket, request) -> {
//                App.log("New WebSocket client connected");
//                webSocket.setClosedCallback(new CompletedCallback() {
//                    @Override
//                    public void onCompleted(Exception e) {
//                        try {
//                            if (e != null) {
//                                e.printStackTrace();
//                            }
//                        } finally {
//                            mLocalBroadcastManager.sendBroadcast(
//                                    new Intent(App.LOCAL_ACTION_CONNECTION_CLOSED)
//                                            .putExtra("type", "web")
//                                            .putExtra("name", webSocket.toString()));
//                            mWebSockets.remove(webSocket);
//                            updateNotificationText();
//                        }
//                    }
//                });
//                webSocket.setStringCallback(message -> mLocalBroadcastManager.sendBroadcast(
//                        new Intent(App.LOCAL_ACTION_COMMAND_RECEIVED)
//                                .putExtra("from", "web")
//                                .putExtra("command", message)));
//                mWebSockets.add(webSocket);
//                if (isConnectionStateMessageEnabled()) {
//                    webSocket.send(App.ACTION_CONNECTION_ESTABLISHED);
//                }
//                mLocalBroadcastManager.sendBroadcast(
//                        new Intent(App.LOCAL_ACTION_CONNECTION_ESTABLISHED)
//                                .putExtra("type", "web")
//                                .putExtra("name", webSocket.toString()));
//                updateNotificationText();
//            });
//        }
//    }

//    private void stopWebServer() {
//        if (mWebSockets != null) {
//            for (WebSocket socket : mWebSockets) {
//                socket.close();
//            }
//            mWebSockets = null;
//        }
//
//        if (mWebServer != null) {
//            mWebServer.stop();
//            mWebServer = null;
//        }
//    }

//    public void webSocketSend(String message) {
//        if (mWebSockets != null) {
//            for (WebSocket socket : mWebSockets) {
//                socket.send(message);
//            }
//        }
//    }

//    private void startSerialCommunication() {
//        if (isCommunicationTypeEnabled("serial")) {
//            stopSerialCommunication();
//            mSerialPort = new SerialPort(
//                    App.getInstance().getStringPreference("serial_path"),
//                    App.getInstance().getIntPreference("serial_baud_rate"),
//                    this);
//        }
//    }

//    private void stopSerialCommunication() {
//        if (mSerialPort != null) {
//            mSerialPort.closePort();
//            mSerialPort = null;
//        }
//    }

//    private void serialSend(String message) {
//        if (mSerialPort != null && mSerialPort.isConnected()) {
//            mSerialPort.write(message.getBytes());
//        }
//    }

    private void sendData(String message) {
        if (App.getInstance().getBooleanPreference("crlf")) {
            message += App.CRLF;
        }

        for (BaseCommunicator communicator : communicators.values()) {
            communicator.write(message);
        }

//        mUsbConnection.send(message);
//        bluetoothSend(message);
//        webSocketSend(message);
//        serialSend(message);

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(App.LOCAL_ACTION_DATA_SENT).putExtra("data", message));
    }

    private void sendActionComplete(String id) {
        Intent intent = new Intent(App.ACTION_SEND_DATA_COMPLETE);
        intent.putExtra("id", id);
        sendBroadcast(intent);
    }

    private int mLastLightSensorMode = 0;
    private long mLastLightSensorMillis = 0;

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float value = event.values[0];
            int mode = 0;

            if (value >= SensorManager.LIGHT_FULLMOON) {
                mode++;
            }
            if (value >= SensorManager.LIGHT_CLOUDY) {
                mode++;
            }
            if (value >= SensorManager.LIGHT_SUNRISE) {
                mode++;
            }
            if (value >= SensorManager.LIGHT_OVERCAST) {
                mode++;
            }
            if (value >= SensorManager.LIGHT_SHADE) {
                mode++;
            }
            if (value >= SensorManager.LIGHT_SUNLIGHT) {
                mode++;
            }
            if (value >= SensorManager.LIGHT_SUNLIGHT_MAX) {
                mode++;
            }

            if (mLastLightSensorMode != mode
                    && System.currentTimeMillis() - mLastLightSensorMillis > 3000) {
                mLastLightSensorMillis = System.currentTimeMillis();
                mLastLightSensorMode = mode;

                if (mPrefs != null && mPrefs.getBoolean("send_light_sensor_data",
                        getResources().getBoolean(R.bool.pref_default_send_light_sensor_data))) {
                    sendData(String.format(Locale.getDefault(),
                            getString(R.string.send_data_to_controller_format),
                            "light_sensor_value", String.valueOf(value)));
                    sendData(String.format(Locale.getDefault(),
                            getString(R.string.send_data_to_controller_format),
                            "light_sensor_mode", String.valueOf(mode)));
                }
            }
        }
    }

    private void updateNotificationTextItem(BaseCommunicator communicator) {
        if (mNotificationLayout == null) {
            return;
        }

        String communicatorTypeCode = communicator.getCommunicatorType().getTypeCode();
        mNotificationLayout.setTextViewText(
                communicator.getCommunicatorType().getCountTextViewId(),
                String.valueOf(communicator.getConnectionsCount()));

        setNotificationInfoVisibility(communicatorTypeCode, isCommunicationTypeEnabled(communicatorTypeCode) ? View.VISIBLE : View.GONE);
        App.log(communicator.getCommunicatorType().getTypeCode() + "-" + (isCommunicationTypeEnabled(communicatorTypeCode) ? "VISIBLE" : "GONE"));

        if (CommunicatorType.WEB == communicator.getCommunicatorType()) {
            mNotificationLayout.setTextViewText(R.id.ip_address,
                    Utils.getIpAddress() + ":" + App.getInstance().getIntPreference("web_server_port",
                            getString(R.string.pref_default_web_server_port)));
            mNotificationLayout.setViewVisibility(R.id.ip_address, isCommunicationTypeEnabled(communicatorTypeCode) ? View.VISIBLE : View.GONE);
        }
    }

    public boolean isConnectionStateMessageEnabled() {
        return App.getInstance().getBooleanPreference("send_connection_state");
    }
}