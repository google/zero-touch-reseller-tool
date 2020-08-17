// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.zero_touchresellertool;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


import com.google.zero_touchresellertool.ApsService;
import com.google.zero_touchresellertool.MainActivity;
import com.google.api.services.androiddeviceprovisioning.v1.model.DeviceIdentifier;
import com.google.gson.Gson;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class AddDevice extends AppCompatActivity {
    private static final String TAG = AddDevice.class.getSimpleName();
    public static String RESELLER_ID = "reseller_id";
    public static String CUSTOMER_ID = "customer_id";
    private static Long reseller_id = 0L;
    private static Long customer_id = 0L;
    static final String RETURN_RESULT = "AddDeviceReturn";
    static Activity This;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_device);

        Intent intent = getIntent();
        customer_id = intent.getLongExtra(CUSTOMER_ID, 0L);
        reseller_id = intent.getLongExtra(RESELLER_ID, 0L);
        This = this;
    }

    public void switch_device_type(View view) {
        ToggleButton device_type = (ToggleButton) findViewById(R.id.device_type);
        if (device_type.isChecked()) {
            //Wi-Fi
            TextView modem_id_label = (TextView) findViewById(R.id.modem_id_label);
            modem_id_label.setEnabled(false);
            EditText modem_id = (EditText) findViewById(R.id.modem_id);
            modem_id.setEnabled(false);
            Button modem_type = (Button) findViewById(R.id.modem_type);
            modem_type.setEnabled(false);
            TextView modem_type_label = (TextView) findViewById(R.id.modem_type_label);
            modem_type_label.setEnabled(false);
            TextView model_label = (TextView) findViewById(R.id.model_label);
            model_label.setEnabled(true);
            EditText model = (EditText) findViewById(R.id.model);
            model.setEnabled(true);
            TextView serial_number_label = (TextView) findViewById(R.id.serial_number_label);
            serial_number_label.setEnabled(true);
            EditText serial_number = (EditText) findViewById(R.id.serial_number);
            serial_number.setEnabled(true);
        } else {
            //Cellular
            TextView modem_id_label = (TextView) findViewById(R.id.modem_id_label);
            modem_id_label.setEnabled(true);
            EditText modem_id = (EditText) findViewById(R.id.modem_id);
            modem_id.setEnabled(true);
            Button modem_type = (Button) findViewById(R.id.modem_type);
            modem_type.setEnabled(true);
            TextView modem_type_label = (TextView) findViewById(R.id.modem_type_label);
            modem_type_label.setEnabled(true);
            TextView model_label = (TextView) findViewById(R.id.model_label);
            model_label.setEnabled(false);
            EditText model = (EditText) findViewById(R.id.model);
            model.setEnabled(false);
            TextView serial_number_label = (TextView) findViewById(R.id.serial_number_label);
            serial_number_label.setEnabled(false);
            EditText serial_number = (EditText) findViewById(R.id.serial_number);
            serial_number.setEnabled(false);
        }
    }

    public void submit_device(View view) {
        DeviceIdentifier deviceIdentifier = new DeviceIdentifier();
        TextView manufacturer = (TextView) findViewById(R.id.manufacturer);
        deviceIdentifier.setManufacturer(manufacturer.getText().toString());
        ToggleButton device_type = (ToggleButton) findViewById(R.id.device_type);
        if (device_type.isChecked()) {
            // Wi-Fi
            EditText model = (EditText) findViewById(R.id.model);
            deviceIdentifier.setModel(model.getText().toString());
            EditText serial_number = (EditText) findViewById(R.id.serial_number);
            deviceIdentifier.setSerialNumber(serial_number.getText().toString());
        }
        else {
            // Cellular
            ToggleButton modem_type = (ToggleButton) findViewById(R.id.modem_type);
            EditText modem_id = (EditText) findViewById(R.id.modem_id);
            if (modem_type.isChecked()) {
                // MEID
                String meid = modem_id.getText().toString().trim();
                if (meid.length() != 14) {
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.invalid_MEID, Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }
                deviceIdentifier.setMeid(meid);
            } else {
                // IMEI
                String imei = modem_id.getText().toString().trim();
                if (imei.length()!=15) {
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.invalid_IMEI_length, Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }
                if (!validIMEI(imei)) {
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.invalid_IMEI, Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }
                deviceIdentifier.setImei(imei);
            }
        }
        PendingIntent pendingResult = createPendingResult(
                ApsService.APS_CODE, new Intent(), 0);
        Intent intent = new Intent(getApplicationContext(), ApsService.class);
        intent.putExtra(ApsService.COMMAND, ApsService.COMMAND_ADD_DEVICE);
        intent.putExtra(ApsService.PENDING_RESULT_EXTRA, pendingResult);
        intent.putExtra(ApsService.RESELLER_ID, reseller_id);
        intent.putExtra(ApsService.CUSTOMER_ID, customer_id);
        Gson gson = new Gson();
        String deviceAsString = gson.toJson(deviceIdentifier);
        intent.putExtra(ApsService.DEVICE, deviceAsString);
        startService(intent);
    }

    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra(ApsService.COMMAND);
            if (command == null) {
                Log.e(TAG, "No command given to AddDevice broadcast receiver");
                return;
            }
            switch (command) {
                case ApsService.COMMAND_ADD_DEVICE: {
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.device_added_success, Toast.LENGTH_SHORT);
                    toast.show();
                    Intent maintent = new Intent(AddDevice.this,
                            MainActivity.class);
                    startActivity(maintent);
                    break;
                }
                case ApsService.ERROR: {
                    String error = intent.getStringExtra(ApsService.ERROR);
                    int toastmessage = R.string.error_message;
                    if (error != null) {
                        switch (error) {
                            case ApsService.ERROR_INVALID_MANUFACTURER: {
                                toastmessage = R.string.error_invalid_manufacturer;
                                break;
                            }
                            case ApsService.ERROR_COMMUNICATION: {
                                toastmessage = R.string.error_communication;
                                break;
                            }
                            case ApsService.ERROR_RECLAIM: {
                                toastmessage = R.string.error_reclaim;
                                break;
                            }
                            case ApsService.ERROR_ANOTHER_CUSTOMER: {
                                toastmessage = R.string.error_another_customer;
                                break;
                            }
                            case ApsService.ERROR_BAD_MODEL: {
                                toastmessage = R.string.error_bad_model;
                                break;
                            }
                        }
                    }
                    Toast toast = Toast.makeText(getApplicationContext(), toastmessage, Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                }
            }
        }
    };

    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, new IntentFilter(RETURN_RESULT));
    }

    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bReceiver);
    }

    private boolean validIMEI(String imei) {
        if (imei==null) {
            return false;
        }
        if (imei.length() != 15) {
            return false;
        }
        int sum = 0, number = 0;
        for (int i=0; i<15; i++) {
            char c = imei.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            } else {
                number = c - '0';
            }
            if ((i+1)%2==0) {
                number = number * 2;
                number = number / 10  + number % 10;
            }
            sum += number;
        }
        return sum % 10 == 0;
    }

    public void scan_modem_id(View view) {
        IntentIntegrator intentIntegrator = new IntentIntegrator(This);
        intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.ONE_D_CODE_TYPES);
        intentIntegrator.setOrientationLocked(false);
        intentIntegrator.initiateScan();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                EditText modemId = findViewById(R.id.modem_id);
                modemId.setText(result.getContents());
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    private void switch_to_portrait() {
        EditText manufacture = findViewById(R.id.manufacturer);
        String manufactureValue = manufacture.getText().toString();
        EditText modemId = findViewById(R.id.modem_id);
        String modemIdValue = modemId.getText().toString();
        EditText model = findViewById(R.id.model);
        String modelValue = model.getText().toString();
        EditText serialNumber = findViewById(R.id.serial_number);
        String serialNumberValue = serialNumber.getText().toString();
        ToggleButton deviceType = findViewById(R.id.device_type);
        boolean deviceTypeValue = deviceType.isChecked();
        ToggleButton modemType = findViewById(R.id.modem_type);
        boolean modemTypeValue = modemType.isChecked();
        setContentView(R.layout.add_device);
        manufacture = findViewById(R.id.manufacturer);
        modemId = findViewById(R.id.modem_id);
        model = findViewById(R.id.model);
        serialNumber = findViewById(R.id.serial_number);
        deviceType = findViewById(R.id.device_type);
        modemType = findViewById(R.id.modem_type);
        manufacture.setText(manufactureValue);
        modemId.setText(modemIdValue);
        model.setText(modelValue);
        serialNumber.setText(serialNumberValue);
        deviceType.setChecked(deviceTypeValue);
        modemType.setChecked(modemTypeValue);
        switch_device_type(null);
    }

    private void switchToLandscape() {
        EditText manufacture = findViewById(R.id.manufacturer);
        String manufactureValue = manufacture.getText().toString();
        EditText modemId = findViewById(R.id.modem_id);
        String modemIdValue = modemId.getText().toString();
        EditText model = findViewById(R.id.model);
        String modelValue = model.getText().toString();
        EditText serialNumber = findViewById(R.id.serial_number);
        String serialNumberValue = serialNumber.getText().toString();
        ToggleButton deviceType = findViewById(R.id.device_type);
        boolean deviceTypeValue = deviceType.isChecked();
        ToggleButton modemType = findViewById(R.id.modem_type);
        boolean modemTypeValue = modemType.isChecked();
        setContentView(R.layout.add_device_land);
        manufacture = findViewById(R.id.manufacturer);
        modemId = findViewById(R.id.modem_id);
        model = findViewById(R.id.model);
        serialNumber = findViewById(R.id.serial_number);
        deviceType = findViewById(R.id.device_type);
        modemType = findViewById(R.id.modem_type);
        manufacture.setText(manufactureValue);
        modemId.setText(modemIdValue);
        model.setText(modelValue);
        serialNumber.setText(serialNumberValue);
        deviceType.setChecked(deviceTypeValue);
        modemType.setChecked(modemTypeValue);
        switch_device_type(null);
    }


    @Override
    public void onConfigurationChanged(@NonNull Configuration config) {
        super.onConfigurationChanged(config);
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            switch_to_portrait();
        } else if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            switchToLandscape();
        }
    }

}
