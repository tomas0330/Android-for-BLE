package com.hossameid.blescanner.presentation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.hossameid.blescanner.R;
import com.hossameid.blescanner.databinding.ActivityMainBinding;
import com.hossameid.blescanner.system.MyBleForegroundService;
import com.hossameid.blescanner.utils.MACAddressValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private BluetoothViewModel viewModel;
    private Context context;
    private MyBleForegroundService myService;
    private boolean isBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = ViewModelProvider.AndroidViewModelFactory
                .getInstance(getApplication()).create(BluetoothViewModel.class);

        checkBLEAvailability();

        checkAndRequestPermissions();

        subscribeToObservers();

        registerBroadcastReceivers();

        binding.connectBtn.setOnClickListener(v -> onConnectBtnClick());

        binding.disconnectBtn.setOnClickListener(v -> onDisconnectBtnClick());

        bindToBleService();
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyBleForegroundService.LocalBinder binder = (MyBleForegroundService.LocalBinder) service;
            myService = binder.getService();
            isBound = true;

            // Check if still connected
            boolean isConnected = myService.isDeviceConnected();
            if (isConnected) {
                binding.connectionStatusTextView.setText(ContextCompat.getString(context, R.string.connected));
                binding.uuidTextView.setText(myService.getUuid());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    private void bindToBleService()
    {
        // Bind to MyBleService
        context = this;
        Intent intent = new Intent(this, MyBleForegroundService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void registerBroadcastReceivers()
    {
        // Register the connection status receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionStatusReceiver,
                new IntentFilter("ACTION_CONNECTION_STATUS"));

        // Register the characteristic value receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(characteristicValueReceiver,
                new IntentFilter("ACTION_CHARACTERISTIC_VALUE"));

        // Register the characteristic value receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(characteristicUuidReceiver,
                new IntentFilter("ACTION_CHARACTERISTIC_UUID"));
    }

    private void subscribeToObservers() {
        viewModel.getScanResult().observe(this, scanResult -> {
            switch (scanResult) {
                case "Scanning":
                    binding.connectionStatusTextView.setText(
                            ContextCompat.getString(this, R.string.scanning));
                    binding.connectBtn.setEnabled(false);
                    break;
                case "device found":
                    binding.connectionStatusTextView.setText(
                            ContextCompat.getString(this, R.string.connected));
                    Toast.makeText(this, "Device found", Toast.LENGTH_SHORT).show();
                    startForegroundService();
                    binding.connectBtn.setEnabled(true);
                    break;
                case "device not found":
                    binding.connectionStatusTextView.setText(
                            ContextCompat.getString(this, R.string.disconnected));
                    Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show();
                    binding.connectBtn.setEnabled(true);
                    break;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, MyBleForegroundService.class);
        serviceIntent.putExtra("bluetooth_device", viewModel.getDevice());
        serviceIntent.putExtra("characteristic",
                Objects.requireNonNull(binding.characteristicEditText.getText()).toString());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void onConnectBtnClick() {
        List<String> permissions = checkPermissions();

        // Check if the permissions were granted
        if (!permissions.isEmpty()) {
            Toast.makeText(this, "Permissions Denied!", Toast.LENGTH_SHORT).show();
        } else {
            binding.macAddressLayout.setError(null);
            binding.usernameEditText.setError(null);

            String macAddress = Objects.requireNonNull(binding.macAddressEditText.getText()).toString();
            String name = Objects.requireNonNull(binding.usernameEditText.getText()).toString();

            if (macAddress.isEmpty() && name.isEmpty()) {
                binding.macAddressLayout.setError("Please enter a MAC address or a name");
                binding.usernameLayout.setError("Please enter a name or a MAC address");
                return;
            }

            if (!macAddress.isEmpty() && !MACAddressValidator.isValidMACAddress(macAddress)) {
                binding.macAddressLayout.setError("Invalid MAC address");
                return;
            }

            // Start scanning
            viewModel.scanLeDevice(macAddress, name);
        }
    }

    private void onDisconnectBtnClick()
    {
        //Kill the background service which will disconnect the BLE client
        myService.stopService();

        //Reset the UI
        binding.connectionStatusTextView.setText(ContextCompat.getString(this, R.string.disconnected));
        binding.uuidTextView.setText(ContextCompat.getString(this, R.string.n_a));
    }

    private final BroadcastReceiver connectionStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            // Update the UI with the connection status
            binding.connectionStatusTextView.setText(status);
        }
    };

    private final BroadcastReceiver characteristicValueReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String value = intent.getStringExtra("value");
            // Update the UI with the new characteristic value
            binding.valueTextView.setText(value);
        }
    };

    private final BroadcastReceiver characteristicUuidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String uuid = intent.getStringExtra("uuid");
            // Update the UI with the new characteristic value
            binding.uuidTextView.setText(uuid);
        }
    };

    private void checkBLEAvailability() {
        // Check to see if the BLE feature is available.
        boolean bluetoothLEAvailable = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);

        if (!bluetoothLEAvailable) {
            Toast.makeText(this, "BLE not available", Toast.LENGTH_LONG).show();
        }
    }

    // Define the required permissions based on the Android version
    private final String[] AppPermissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    }
            : new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // Register the ActivityResultLauncher to request multiple permissions
    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> permissions) {
                    boolean allPermissionsGranted = true;

                    // Iterate through the permissions and check if all are granted
                    for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                        boolean isGranted = entry.getValue();
                        if (!isGranted) {
                            allPermissionsGranted = false;
                            break;
                        }
                    }

                    // Enable or disable buttons based on the permission status
                    if (allPermissionsGranted) {
                        binding.connectBtn.setEnabled(true);
                        binding.disconnectBtn.setEnabled(true);
                    } else {
                        binding.connectBtn.setEnabled(false);
                        binding.disconnectBtn.setEnabled(false);
                        Toast.makeText(MainActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    // Method to check and request the necessary permissions
    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = checkPermissions();

        // If there are any permissions that are not yet granted, request them
        if (!permissionsToRequest.isEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            // All permissions are already granted, enable the buttons
            binding.connectBtn.setEnabled(true);
            binding.disconnectBtn.setEnabled(true);
        }
    }

    // Method to check which permissions are not yet granted
    private List<String> checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : AppPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        return permissionsToRequest;
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
}
