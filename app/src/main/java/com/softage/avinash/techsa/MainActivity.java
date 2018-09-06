package com.softage.avinash.techsa;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ToggleButton;

import com.softage.avinash.techsa.constants.Constants;
import com.softage.avinash.techsa.threads.ClientThread;
import com.softage.avinash.techsa.threads.DeviceConnectThread;
import com.softage.avinash.techsa.threads.ServerConnectThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_DISCOVERABLE_BT = 0;

    private static final String TAG = "Bluetooth";
    public static String MacAddress;
    private BluetoothSocket socket;
    ListView listView;
    ArrayList<BluetoothDevice> devices;
    ArrayList<String> allDevices;
    private BluetoothDevice deviceToConnect;

    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    private BluetoothSocket curBTSocket = null;

    ClientThread connectThread;
    DeviceConnectThread deviceConnectThread;
    ServerConnectThread serverConnectThread;

    AlertDialog alertDialogObject;
    ArrayAdapter<String> devicesListAdapter;
    public ProgressDialog progressBar;
    ToggleButton Bluetooth_on;
    EditText edtMessage;
    public static final String DEV_CONNECTED = "DEVICE_CONNECTED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Bluetooth_on = (ToggleButton) findViewById(R.id.Bluetooth_on);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bReciever, filter);
        listView = (ListView) findViewById(R.id.list1);
        progressBar = new ProgressDialog(MainActivity.this);
        Bluetooth_on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    progressBar.setCancelable(true);
                    progressBar.setMessage("Please Wait....");
                    progressBar.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    progressBar.show();
                    if (checkCompatibility()) {
                        turnOn();
                        progressBar.dismiss();
                        startDiscovery();
                        makeDiscoverable();
                    }
                    // The toggle is enabled
                } else {
                    turnOff();
                    // The toggle is disabled
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBluetoothAdapter.isEnabled()) {
            startAsServer();
        }
    }

    private void turnOn() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void makeDiscoverable() {
        if (!mBluetoothAdapter.isDiscovering()) {
//            showMessage("Making Discoverable...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(enableBtIntent, REQUEST_DISCOVERABLE_BT);
        }
    }

    private void disconnect() {
        if (curBTSocket != null) {
            try {
                curBTSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private void startDiscovery() {
//        showMessage("Starting Discovery...");
        getPairedDevices();
        mBluetoothAdapter.startDiscovery();
    }

    private void cancelDiscovery() {
//        showMessage("Cancelling Discovery...");
        unregisterReceiver(bReciever);
        mBluetoothAdapter.cancelDiscovery();
    }

    private void getPairedDevices() {

        if (devices == null)
            devices = new ArrayList<BluetoothDevice>();
        else
            devices.clear();

        @SuppressLint("MissingPermission") Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice curDevice : pairedDevices) {
                devices.add(curDevice);
            }
            Log.i(TAG, "Paired Number of Devices : " + pairedDevices.size());
            showPairedList();
        }
    }

    private void turnOff() {
        cancelDiscovery();
        mBluetoothAdapter.disable();
    }

    private boolean checkCompatibility() {
        // Phone does not support Bluetooth so let the user know and exit.
        if (mBluetoothAdapter == null) {
//            showMessage("Your phone does not support Bluetooth");
            return false;
        } else {
//            showMessage("Your phone supports Bluetooth ");
            return true;
        }
    }

    private final BroadcastReceiver bReciever = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice curDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                devices.add(curDevice);
            }
            Log.i(TAG, "All BT Devices : " + devices.size());
            if (devices.size() > 0) {
                showPairedList();
            }
        }
    };

    public void connectAsClient() {
//        showMessage("Connecting for online Bluetooth devices...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (deviceToConnect != null) {
                    if (connectThread != null) {
                        connectThread.cancel();
                        connectThread = null;
                    }
                    connectThread = new ClientThread();
                    curBTSocket = connectThread.connect(mBluetoothAdapter, deviceToConnect, MY_UUID_SECURE, mHandler);
                    connectThread.start();
                }
            }
        }).start();
    }

    public void killServerThread() {
        if (serverConnectThread != null) {
            serverConnectThread.closeConnection();
            serverConnectThread = null;
        }
    }

    private void startAsServer() {
//        showMessage("Listening for online Bluetooth devices...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                serverConnectThread = new ServerConnectThread();
                curBTSocket = serverConnectThread.acceptConnection(mBluetoothAdapter, MY_UUID_SECURE, mHandler);
            }
        }).start();
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            byte[] buf = (byte[]) msg.obj;

            switch (msg.what) {

                case Constants.MESSAGE_WRITE:
                    // construct a string from the buffer
                    String writeMessage = new String(buf);
                    Log.i(TAG, "Write Message : " + writeMessage);
//                    showMessage("Message Sent : " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(buf, 0, msg.arg1);
                    Log.i(TAG, "readMessage : " + readMessage);
//                    showMessage("Message Received : " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    String mConnectedDeviceName = new String(buf);
//                    showMessage("Connected to " + mConnectedDeviceName);
                    sendMessageToDevice();
                    break;
                case Constants.MESSAGE_SERVER_CONNECTED:
//                    showMessage("CONNECTED");
                    Log.d(TAG, "Connected...");
                    break;
            }
        }
    };

    public void sendMessageToDevice() {
        deviceConnectThread = new DeviceConnectThread(curBTSocket, mHandler);
        deviceConnectThread.start();
        String message = edtMessage.getText().toString().trim();
        if (message.length() > 0) {
            byte[] send = message.getBytes();
            deviceConnectThread.write(send);
        }
    }

//    public void showMessage(String message) {
//        Snackbar snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG);
//        View view = snackbar.getView();
//        view.setBackgroundColor(Color.GREEN);
//        TextView textView = (TextView) view.findViewById(android.support.design.R.id.snackbar_text);
//        textView.setTextColor(Color.BLACK);
//        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) view.getLayoutParams();
//        params.gravity = Gravity.BOTTOM;
//        view.setLayoutParams(params);
//        snackbar.show();
//    }

    public void showPairedList() {

        List<String> tempDevices = new ArrayList<String>();

        for (BluetoothDevice b : devices) {
            String paired = "Paired";
            if (b.getBondState() != 12) {
                paired = "Not Paired";
            }
            tempDevices.add(b.getName());
        }

        if (allDevices == null)
            allDevices = new ArrayList<String>();
        else
            allDevices.clear();

        allDevices.addAll(tempDevices);

        if (devicesListAdapter == null) {

            devicesListAdapter = new ArrayAdapter<>(this,
                    R.layout.bluetooth_item, R.id.devices, allDevices);
            listView.setAdapter(devicesListAdapter);
            //Create sequence of items
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setTitle("Paired/Unpaired BT Devices");
            dialogBuilder.setView(listView);
            //Create alert dialog object via builder
            final AlertDialog alertDialogObject = dialogBuilder.create();
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    deviceToConnect = devices.get(position);
                    devicesListAdapter = null;
                    alertDialogObject.dismiss();
                    Log.i(TAG, "Connecting to device :" + deviceToConnect.getName());
//                    showMessage("Connecting to device " + deviceToConnect.getName());
                    setMacAddress(deviceToConnect.getAddress());
                    Log.d(TAG, "MacAddress : " + getMacAddress());
                    //Now this is not the server...
                    killServerThread();

                    //Connect to the other device which is a server...
                    connectAsClient();
                    startActivity(new Intent(MainActivity.this,JoyStick.class));
                }
            });
        } else {
            devicesListAdapter.notifyDataSetChanged();
        }
    }

    public void setMacAddress(String MacAddress) {
        MainActivity.MacAddress = MacAddress;
    }

    public static String getMacAddress() {
        return MacAddress;
    }

}