// RnnusbModule.java

package com.rnlibrnnusb;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

public class RnnusbModule extends ReactContextBaseJavaModule {
    private static final String TAG = "ReactNative";
    private final ReactApplicationContext reactContext;
    private static final String ACTION_USB_PERMISSION = "com.rnlibrnnusb.USB_PERMISSION";
    private static final int THREAD_STOP_INTERVAL = 100;
    private static boolean stopReadingThread = true;
    private final Object locker = new Object();
    private UsbManager manager;
    private UsbDevice device;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;
    private UsbDeviceConnection connection;
    UsbRequest usbRequest;
    private PendingIntent usbPermissionIntent;
    private int PID;
    private int VID;

    private Thread readThread;
    private static final String EVENT_USB_RECV_DATA = "USB_RECV_DATA";
    private static final String EVENT_USB_CONNECTION = "USB_CONNECTION";
    private static final String MSG_USB_CONNECTION_ATTACHED = "Attached";
    private static final String MSG_USB_CONNECTION_DETACHED = "Detached";
    private static final String EVENT_USB_INFORM = "USB_INFORM";
    private static final String MSG_PERMISSION_GRANTED = "PERMISSION_GRANTED";
    private static final String MSG_PERMISSION_DENIED = "PERMISSION_DENIED";

    public RnnusbModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        try {
            manager = (UsbManager) this.reactContext.getSystemService(Context.USB_SERVICE);
            // intent - permission
            usbPermissionIntent = PendingIntent.getBroadcast(
                    this.reactContext,
                    0,
                    new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_MUTABLE);
            IntentFilter filterPermission = new IntentFilter(ACTION_USB_PERMISSION);
            this.reactContext.registerReceiver(usbPermissionReceiver, filterPermission);
            // intent - usb attach or detach
            IntentFilter filterUSBAttach = new IntentFilter();
            filterUSBAttach.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filterUSBAttach.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            this.reactContext.registerReceiver(usbAttachReceiver, filterUSBAttach);
        } catch (Exception e) {
            Log.w(TAG, "err: " + e.getMessage());
        }
    }

    private void useDevice() {
        if (device.getInterfaceCount() != 1) {
            emit(EVENT_USB_INFORM, "Could not find device interface");
            return;
        }
        UsbInterface usbInterface = device.getInterface(0);

        // device should have two endpoints
        if (usbInterface.getEndpointCount() != 2) {
            emit(EVENT_USB_INFORM, "Could not find device endpoints");
            return;
        }

        UsbEndpoint endpointIn = usbInterface.getEndpoint(0);
        if (endpointIn.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            emit(EVENT_USB_INFORM, "First endpoint is not interrupt type");
            return;
        }
        if (endpointIn.getDirection() != UsbConstants.USB_DIR_IN) {
            emit(EVENT_USB_INFORM, "First endpoint direction is not in");
            return;
        }

        // second endpoint should be of type interrupt with direction of out
        UsbEndpoint endpointOut = usbInterface.getEndpoint(1);
        if (endpointOut.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            emit(EVENT_USB_INFORM, "Second endpoint is not interrupt type");
            return;
        }
        if (endpointOut.getDirection() != UsbConstants.USB_DIR_OUT) {
            emit(EVENT_USB_INFORM, "Second endpoint direction is not out");
            return;
        }

        this.endpointIn = endpointIn;
        this.endpointOut = endpointOut;

        try {
            connection = manager.openDevice(device);
            connection.claimInterface(usbInterface, true);
            Log.i(TAG, "useDevice: connection opened!");
            if (readThread == null) {
                Log.i(TAG, "useDevice: start reader thread");
                readThread = new Thread(reader);
                readThread.start();
            }
            stopReadingThread = false;
            emit(EVENT_USB_CONNECTION, MSG_USB_CONNECTION_ATTACHED);
        } catch (Exception e) {
            Log.i(TAG, "useDevice err: " + e.getMessage());
            emit(EVENT_USB_INFORM, e.getMessage());
        }
    }

    private void emit(String event, String Data) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(event, Data);
    }

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "usbPermissionReceiver: intent.getAction: " + action);
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // call method to set up device communication
                            Log.i(TAG, "permission granted for device");
                            useDevice();
                            emit(EVENT_USB_INFORM, MSG_PERMISSION_GRANTED);
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                        emit(EVENT_USB_INFORM, MSG_PERMISSION_DENIED);
                    }
                }
            }
        }
    };

    private final BroadcastReceiver usbAttachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // UsbDevice usbDevice = (UsbDevice)
            // intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            UsbDevice usbDevice = (UsbDevice) intent.getExtras().get("device");
            Log.i(TAG, "usbAttachReceiver: usb dev " + usbDevice.toString());
            if (usbDevice == null || usbDevice.getProductId() != PID || usbDevice.getVendorId() != VID) {
                return;
            }
            Log.i(TAG, "onReceive: intent.getAction = " + action);
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                RnnusbModule.this.device = usbDevice;
                manager.requestPermission(usbDevice, usbPermissionIntent);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (device != null) {
                    // call your method that cleans up and closes communication with the device
                    Log.i(TAG, "onReceive: device != null, closing connection.");
                    synchronized (locker) {
                        if (connection != null) {
                            connection.close();
                        }
                        stopReadingThread = true;
                        // 置空之后 另一个线程有一定概率会爆空指针 所以不要置空
                        // connection = null;
                        // readThread = null;
                        // endpointOut = null;
                        // endpointIn = null;
                        // device = null;
                    }
                    emit(EVENT_USB_CONNECTION, MSG_USB_CONNECTION_DETACHED);
                }
            }
        }
    };

    private String bytesToHexString(byte[] bytes, int offset, int len) {
        if (bytes == null || len == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < len; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() < 2) {
                hex = "0" + hex;
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0, j = 0; i < len; i += 2, j++) {
            data[j] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private final Runnable reader = new Runnable() {
        public void run() {
            int readBufferMaxLength = endpointIn.getMaxPacketSize();
            byte[] bytes = new byte[readBufferMaxLength];
            while (true) {
                if (stopReadingThread) {
                    sleep(THREAD_STOP_INTERVAL);
                    continue;
                }
                int response = connection.bulkTransfer(endpointIn, bytes, readBufferMaxLength, 50);
                if (response >= 0) {
                    String hex = bytesToHexString(bytes, 0, readBufferMaxLength);
                    // Log.i(TAG, "USB read: " + hex);
                    emit(EVENT_USB_RECV_DATA, hex);
                }
            }
        }
    };

    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "Rnnusb";
    }

    @ReactMethod
    public void setTarget(int VID, int PID, Promise promise) {
        this.VID = VID;
        this.PID = PID;
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        Log.i(TAG, "Looking for device VID: " + VID + " PID: " + PID);
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            Log.i(TAG, "Find a device with VID " + device.getVendorId() + " & PID " + device.getProductId());
            if (device.getVendorId() == VID && device.getProductId() == PID) {
                this.device = device;
                manager.requestPermission(this.device, usbPermissionIntent);
                break;
            }
        }
    }

    @ReactMethod
    public void write(String data, Promise promise) {
        byte[] bytes = hexStringToBytes(data);
        synchronized (locker) {
            if (connection == null) {
                String error = "No USB connection established";
                Log.e(TAG, error);
                promise.reject("E", error);
                return;
            }
            Log.i(TAG, "write: send " + data);
            connection.bulkTransfer(endpointOut, bytes, bytes.length, 50);
        }
        promise.resolve(null);
    }

    @ReactMethod
    public void echo(String str) {
        Log.i(TAG, "echo: " + str);
    }
}