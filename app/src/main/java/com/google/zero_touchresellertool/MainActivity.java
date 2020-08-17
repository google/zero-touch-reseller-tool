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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;


import com.google.api.services.androiddeviceprovisioning.v1.model.Company;
import com.google.api.services.androiddeviceprovisioning.v1.model.Device;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    static final String RETURN_RESULT = "MainActivityReturn";
    private static final String TAG = MainActivity.class.getSimpleName();
    private static ArrayList<String> customerList = new ArrayList<String>();
    private static ArrayList<ZTDevice> deviceList = new ArrayList<ZTDevice>();
    private static ArrayAdapter<String> customerAdapter;
    private static ArrayAdapter<ZTDevice> deviceAdapter;
    private static List<Company> customers;
    private static boolean setup = false;
    private static long resellerId = 0L;
    private static long customerId = 0L;
    private static String mcServiceAccount = "";
    private static long mcResellerId = 0L;
    private static String mcFirebaseApplicationid = "";
    private static String mcFirebaseApiKey = "";
    private static String mcFirebaseDatabseUrl = "";
    private static String mcFirebaseStorageBucket = "";
    private static String mcFirebaseProjectId = "";
    private static String mcUsername = "";
    private static int selectedCustomer = -1;
    private static int startOfCustomerList = -1;
    private static int topOfCustomerList = -1;
    private static boolean customerListStale = true;
    private static int lastOrientation = Configuration.ORIENTATION_PORTRAIT;
    private static Menu mMenu;
    BroadcastReceiver restrictionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final RestrictionsManager myRestrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
            // Get the current configuration bundle
            Bundle appRestrictions = myRestrictionsManager.getApplicationRestrictions();
            Log.d(TAG, "appRestrictions = " + appRestrictions);
            if (appRestrictions != null) {
                processManagedConfiguration(appRestrictions);
                readPreferences();
                Toast toast = Toast.makeText(getApplicationContext(), R.string.managed_configuration_updated, Toast.LENGTH_LONG);
                toast.show();
                refreshCustomerList();
            }
        }
    };
    private ActionMode mActionMode;
    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mMenu = menu;
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.context_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    ListView deviceListView = findViewById(R.id.listview_devices);
                    SparseBooleanArray selected = deviceListView.getCheckedItemPositions();
                    ArrayList<ZTDevice> deleteList = new ArrayList<ZTDevice>(1);
                    for (int i = selected.size(); i-- > 0; ) {
                        if (selected.valueAt(i)) {
                            ZTDevice device = new ZTDevice();
                            device = deviceList.get(selected.keyAt(i));
                            deleteList.add(device);
                        }
                    }
                    if (!deleteList.isEmpty()) {
                        PendingIntent pendingResult = createPendingResult(com.google.zero_touchresellertool.ApsService.APS_CODE, new Intent(), 0);
                        Intent intent = new Intent(getApplicationContext(), com.google.zero_touchresellertool.ApsService.class);
                        intent.putExtra(com.google.zero_touchresellertool.ApsService.COMMAND, com.google.zero_touchresellertool.ApsService.COMMAND_DELETE_DEVICES);
                        intent.putExtra(com.google.zero_touchresellertool.ApsService.PENDING_RESULT_EXTRA, pendingResult);
                        intent.putExtra(com.google.zero_touchresellertool.ApsService.RESELLER_ID, resellerId);
                        intent.putExtra(com.google.zero_touchresellertool.ApsService.DEVICES, deleteList);
                        startService(intent);
                    }
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    };
    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra(com.google.zero_touchresellertool.ApsService.COMMAND);
            if (command == null) {
                Log.e(TAG, "No command given to MainActivity broadcast receiver");
                return;
            }
            final ListView deviceListView = findViewById(R.id.listview_devices);
            switch (command) {
                case com.google.zero_touchresellertool.ApsService.RESPONSE_LIST_CUSTOMERS:
                    customers = (List<Company>) intent.getSerializableExtra(com.google.zero_touchresellertool.ApsService.CUSTOMER_RESULT);
                    customerAdapter.clear();
                    if (customers != null) {
                        for (Company customer : customers) {
                            customerAdapter.add(customer.getCompanyName());
                        }
                        customerAdapter.notifyDataSetChanged();
                        Toast toast = Toast.makeText(getApplicationContext(), R.string.customer_list_refreshed, Toast.LENGTH_SHORT);
                        toast.show();
                    } else {
                        Log.d(TAG, "No customers found");
                    }
                    break;
                case com.google.zero_touchresellertool.ApsService.RESPONSE_DEVICE_LIST_FIRST_PAGE: {
                    List<Device> devices = (List<Device>) intent.getSerializableExtra(com.google.zero_touchresellertool.ApsService.DEVICE_RESULT_FIRST_PAGE);
                    deviceAdapter.clear();
                    if (devices != null) {
                        for (Device device : devices) {
                            ZTDevice ztDevice = new ZTDevice();
                            ztDevice.setDeviceId(device.getDeviceId());
                            ztDevice.setDeviceIdentifier(device.getDeviceIdentifier());
                            deviceAdapter.add(ztDevice);
                        }
                        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                if (deviceListView.getCheckedItemCount() == 0) {
                                    mActionMode.finish();
                                    mActionMode = null;
                                } else {
                                    mActionMode = startSupportActionMode(mActionModeCallback);
                                }
                            }
                        });
                    } else {
                        Toast toast = Toast.makeText(getApplicationContext(), R.string.no_devices, Toast.LENGTH_SHORT);
                        toast.show();
                    }
                    Button addButton = (Button) findViewById(R.id.add_button);
                    addButton.setVisibility(View.VISIBLE);
                    deviceAdapter.notifyDataSetChanged();
                    break;
                }
                case com.google.zero_touchresellertool.ApsService.RESPONSE_DEVICE_LIST_ADDITIONAL_PAGE: {
                    List<Device> devices = (List<Device>) intent.getSerializableExtra(com.google.zero_touchresellertool.ApsService.DEVICE_RESULT_ADDITIONAL_PAGE);
                    if (devices != null) {
                        for (Device device : devices) {
                            ZTDevice ztDevice = new ZTDevice();
                            ztDevice.setDeviceId(device.getDeviceId());
                            ztDevice.setDeviceIdentifier(device.getDeviceIdentifier());
                            deviceAdapter.add(ztDevice);
                        }
                    }
                    deviceAdapter.notifyDataSetChanged();
                    break;
                }
                case com.google.zero_touchresellertool.ApsService.RESPONSE_DELETE_DEVICES: {
                    ArrayList<Long> deltedDevices = (ArrayList<Long>) intent.getSerializableExtra(com.google.zero_touchresellertool.ApsService.DEVICES);
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.devices_deleted, Toast.LENGTH_SHORT);
                    toast.show();
                    for (Long deviceId : deltedDevices) {
                        for (int i = 0; i < deviceList.size(); i++) {
                            if (deviceList.get(i).getDeviceId().equals(deviceId)) {
                                deviceListView.setItemChecked(i, false);
                                deviceAdapter.remove(deviceList.get(i));
                            }
                        }
                    }
                    deviceAdapter.notifyDataSetChanged();
                    break;
                }
                case com.google.zero_touchresellertool.ApsService.RESPONSE_DELETE_DEVICES_WITH_ERROR: {
                    ArrayList<Long> deletedDevices = (ArrayList<Long>) intent.getSerializableExtra(com.google.zero_touchresellertool.ApsService.DEVICES);
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.devices_deleted_error, Toast.LENGTH_SHORT);
                    toast.show();
                    for (Long deviceId : deletedDevices) {
                        for (int i = 0; i < deviceList.size(); i++) {
                            if (deviceList.get(i).getDeviceId().equals(deviceId)) {
                                deviceListView.setItemChecked(i, false);
                                deviceAdapter.remove(deviceList.get(i));
                            }
                        }
                    }
                    deviceAdapter.notifyDataSetChanged();
                    break;
                }
                case com.google.zero_touchresellertool.ApsService.RESPONSE_ADD_CUSTOMER: {
                    refreshCustomerList();
                    break;
                }
                case com.google.zero_touchresellertool.ApsService.ERROR: {
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.error_message, Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                }
            }
        }
    };

    private void processManagedConfiguration(Bundle restrictions) {
        if (restrictions.containsKey("service_account")) {
            mcServiceAccount = restrictions.getString("service_account", "");
        }
        if (restrictions.containsKey("reseller_id")) {
            try {
                mcResellerId = Long.parseLong(restrictions.getString("reseller_id", "0"));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid reseller_id in managed configuration: " + restrictions.getString("reseller_id", "0"));
            }
        }
        if (restrictions.containsKey("firestore_applicationid")) {
            mcFirebaseApplicationid = restrictions.getString("firestore_applicationid", "");
            Log.d(TAG, mcFirebaseApplicationid);
        }
        if (restrictions.containsKey("firestore_apikey")) {
            mcFirebaseApiKey = restrictions.getString("firestore_apikey", "");
            Log.d(TAG, mcFirebaseApiKey);
        }
        if (restrictions.containsKey("firestore_databaseurl")) {
            mcFirebaseDatabseUrl = restrictions.getString("firestore_databaseurl", "");
            Log.d(TAG, mcFirebaseDatabseUrl);
        }
        if (restrictions.containsKey("firestore_storagebucket")) {
            mcFirebaseStorageBucket = restrictions.getString("firestore_storagebucket", "");
            Log.d(TAG, mcFirebaseStorageBucket);
        }
        if (restrictions.containsKey("firestore_projectid")) {
            mcFirebaseProjectId = restrictions.getString("firestore_projectid", "");
            Log.d(TAG, mcFirebaseProjectId);
        }
        if (restrictions.containsKey("username")) {
            mcUsername = restrictions.getString("username", "");
            Log.d(TAG, mcUsername);
        }
    }

    private void readPreferences() {
        Context context = MainActivity.this;
        final SharedPreferences sharedPref = context.getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        String serviceAccount = "";
        if (mcServiceAccount.equals("")) {
            serviceAccount = sharedPref.getString(getString(R.string.service_account_pref_key), "");
        } else {
            serviceAccount = mcServiceAccount;
        }
        if (mcResellerId == 0L) {
            resellerId = sharedPref.getLong("reseller_id", 0L);
        } else {
            resellerId = mcResellerId;
        }
        if (serviceAccount.equals("") || resellerId == 0L) {
            Intent intent = new Intent(MainActivity.this, com.google.zero_touchresellertool.ServiceAccount.class);
            intent.putExtra(com.google.zero_touchresellertool.ServiceAccount.MC_PARTNER_ID, !(mcResellerId == 0L));
            intent.putExtra(com.google.zero_touchresellertool.ServiceAccount.MC_SERVICE_ACCOUNT, !mcServiceAccount.equals(""));
            startActivity(intent);
        } else {
            com.google.zero_touchresellertool.ApsService.setServiceAccount(serviceAccount);
        }
    }

    private void customerListViewClickHandler(int i) {
        Button addButton = (Button) findViewById(R.id.add_button);
        addButton.setVisibility(View.GONE);
        if (customers != null) {
            selectedCustomer = i;
            customerId = customers.get(i).getCompanyId();
            deviceAdapter.clear();
            deviceAdapter.notifyDataSetChanged();
            ListView deviceListView = findViewById(R.id.listview_devices);
            deviceListView.setOnItemClickListener(null);
            PendingIntent pendingResult = createPendingResult(com.google.zero_touchresellertool.ApsService.APS_CODE, new Intent(), 0);
            Intent intent = new Intent(getApplicationContext(), com.google.zero_touchresellertool.ApsService.class);
            intent.putExtra(com.google.zero_touchresellertool.ApsService.COMMAND, com.google.zero_touchresellertool.ApsService.COMMAND_LIST_DEVICES);
            intent.putExtra(com.google.zero_touchresellertool.ApsService.PENDING_RESULT_EXTRA, pendingResult);
            intent.putExtra(com.google.zero_touchresellertool.ApsService.RESELLER_ID, resellerId);
            intent.putExtra(com.google.zero_touchresellertool.ApsService.CUSTOMER_ID, customerId);
            startService(intent);
        }
    }

    private void firstTimeSetup() {
        if (MainActivity.this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            switchToLandscape();
        }
        final RestrictionsManager myRestrictionsManager = (RestrictionsManager) this.getSystemService(Context.RESTRICTIONS_SERVICE);
        Bundle appRestrictions = myRestrictionsManager.getApplicationRestrictions();
        if (appRestrictions != null) {
            processManagedConfiguration(appRestrictions);
        }
        readPreferences();
        customerList.add("Refreshing...");
        customerAdapter = new ArrayAdapter<String>(this, R.layout.customer_list, customerList);
        deviceAdapter = new ArrayAdapter<ZTDevice>(this, R.layout.device_list, deviceList);
        final ListView customerListView = findViewById(R.id.listview_customers);
        ListView deviceListView = findViewById(R.id.listview_devices);
        customerListView.setAdapter(customerAdapter);
        deviceListView.setAdapter(deviceAdapter);
        customerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                customerListViewClickHandler(i);
            }
        });
        if (!mcFirebaseApiKey.equals("") && !mcFirebaseApplicationid.equals("") && ! mcFirebaseDatabseUrl.equals("") && !mcFirebaseStorageBucket.equals("")) {
            Log.d(TAG, "Connecting to Firestore");
            FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                    .setApplicationId(mcFirebaseApplicationid)
                    .setApiKey(mcFirebaseApiKey)
                    .setDatabaseUrl(mcFirebaseDatabseUrl)
                    .setStorageBucket(mcFirebaseStorageBucket)
                    .setProjectId(mcFirebaseProjectId);
            FirebaseApp.initializeApp(this, builder.build());
            com.google.zero_touchresellertool.ApsService.useDb(true);
            if (!mcUsername.equals("")) {
                com.google.zero_touchresellertool.ApsService.setUsername(mcUsername);
            }
        }
        setup = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!setup) {
            firstTimeSetup();
        }
        readPreferences();
        ListView customerListView = findViewById(R.id.listview_customers);
        ListView deviceListView = findViewById(R.id.listview_devices);
        if (customerListView.getAdapter() == null) {
            customerListView.setAdapter(customerAdapter);
            if (startOfCustomerList >= 0) {
                customerListView.setSelectionFromTop(startOfCustomerList, topOfCustomerList);
            }
            if (selectedCustomer >= 0) {
                customerListView.setItemChecked(selectedCustomer, true);
                customerId = customers.get(selectedCustomer).getCompanyId();
                deviceAdapter.clear();
                deviceAdapter.notifyDataSetChanged();
                deviceListView.setOnItemClickListener(null);
                PendingIntent pendingResult = createPendingResult(com.google.zero_touchresellertool.ApsService.APS_CODE, new Intent(), 0);
                Intent intent = new Intent(getApplicationContext(), com.google.zero_touchresellertool.ApsService.class);
                intent.putExtra(com.google.zero_touchresellertool.ApsService.COMMAND, com.google.zero_touchresellertool.ApsService.COMMAND_LIST_DEVICES);
                intent.putExtra(com.google.zero_touchresellertool.ApsService.PENDING_RESULT_EXTRA, pendingResult);
                intent.putExtra(com.google.zero_touchresellertool.ApsService.RESELLER_ID, resellerId);
                intent.putExtra(com.google.zero_touchresellertool.ApsService.CUSTOMER_ID, customerId);
                startService(intent);
            }
        }
        if (deviceListView.getAdapter() == null) {
            deviceListView.setAdapter(deviceAdapter);
        }
        if (customers == null) {
            refreshCustomerList();
        }
    }

    private void switchToLandscape() {
        ConstraintLayout constraintLayout = findViewById(R.id.mainActivity);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(constraintLayout);
        constraintSet.clear(R.id.listview_customers, ConstraintSet.END);
        constraintSet.connect(R.id.listview_customers, ConstraintSet.BOTTOM, R.id.mainActivity, ConstraintSet.BOTTOM, 24);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        constraintSet.constrainWidth(R.id.listview_customers, (displayMetrics.widthPixels / 3));
        constraintSet.connect(R.id.devices_label, ConstraintSet.START, R.id.listview_customers, ConstraintSet.END, 24);
        constraintSet.connect(R.id.devices_label, ConstraintSet.TOP, R.id.textView2, ConstraintSet.TOP, 0);
        constraintSet.applyTo(constraintLayout);
    }

    private void switch_to_portrait() {
        ConstraintLayout constraintLayout = findViewById(R.id.mainActivity);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(constraintLayout);
        constraintSet.connect(R.id.listview_customers, ConstraintSet.END, R.id.mainActivity, ConstraintSet.END, 24);
        constraintSet.connect(R.id.listview_customers, ConstraintSet.BOTTOM, R.id.guideline, ConstraintSet.TOP, 24);
        constraintSet.constrainWidth(R.id.listview_customers, 0);
        constraintSet.connect(R.id.devices_label, ConstraintSet.START, R.id.listview_customers, ConstraintSet.START, 0);
        constraintSet.connect(R.id.devices_label, ConstraintSet.TOP, R.id.guideline, ConstraintSet.TOP, 0);
        constraintSet.applyTo(constraintLayout);
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

    public void refreshCustomerList() {
        PendingIntent pendingResult = createPendingResult(
                com.google.zero_touchresellertool.ApsService.APS_CODE, new Intent(), 0);
        Intent intent = new Intent(getApplicationContext(), com.google.zero_touchresellertool.ApsService.class);
        intent.putExtra(com.google.zero_touchresellertool.ApsService.COMMAND, com.google.zero_touchresellertool.ApsService.COMMMAND_LIST_CUSTOMERS);
        intent.putExtra(com.google.zero_touchresellertool.ApsService.PENDING_RESULT_EXTRA, pendingResult);
        intent.putExtra(com.google.zero_touchresellertool.ApsService.RESELLER_ID, resellerId);
        startService(intent);
        selectedCustomer = -1;
        startOfCustomerList = -1;
        customerListStale = false;
    }

    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, new IntentFilter(RETURN_RESULT));
        registerReceiver(restrictionsReceiver, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
        if (customerListStale) {
            refreshCustomerList();
        }
        final ListView customerListView = findViewById(R.id.listview_customers);
        if (selectedCustomer >= 0) {
            customerListView.setItemChecked(selectedCustomer, true);
            customerListView.clearFocus();
        }
        customerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                customerListViewClickHandler(i);
            }
        });
        if (getResources().getConfiguration().orientation != lastOrientation) {
            if (lastOrientation == Configuration.ORIENTATION_PORTRAIT) {
                switchToLandscape();
            } else {
                switch_to_portrait();
            }
        }
    }

    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bReceiver);
        unregisterReceiver(restrictionsReceiver);
        ListView customerListView = findViewById(R.id.listview_customers);
        startOfCustomerList = customerListView.getFirstVisiblePosition();
        View v = customerListView.getChildAt(0);
        topOfCustomerList = (v == null) ? 0 : (v.getTop() - customerListView.getPaddingTop());
        customerListView.setOnItemClickListener(null);
        lastOrientation = getResources().getConfiguration().orientation;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_options_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh_customer_list:
                deviceAdapter.clear();
                startOfCustomerList = -1;
                selectedCustomer = -1;
                final ListView customer_listview = findViewById(R.id.listview_customers);
                customer_listview.clearFocus();
                customer_listview.post(new Runnable() {
                    @Override
                    public void run() {
                        customer_listview.setSelection(startOfCustomerList);
                        customer_listview.setItemChecked(selectedCustomer, true);
                        Log.d(TAG, "Selected item " + selectedCustomer);
                    }
                });
                refreshCustomerList();
                return true;
            case R.id.change_service_account:
                Intent intent = new Intent(MainActivity.this, com.google.zero_touchresellertool.ServiceAccount.class);
                intent.putExtra(com.google.zero_touchresellertool.ServiceAccount.MC_PARTNER_ID, !(mcResellerId == 0L));
                intent.putExtra(com.google.zero_touchresellertool.ServiceAccount.MC_SERVICE_ACCOUNT, !mcServiceAccount.equals(""));
                customerListStale = true;
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void add_device(View view) {
        Intent intent = new Intent(MainActivity.this, com.google.zero_touchresellertool.AddDevice.class);
        intent.putExtra(com.google.zero_touchresellertool.AddDevice.RESELLER_ID, resellerId);
        intent.putExtra(com.google.zero_touchresellertool.AddDevice.CUSTOMER_ID, customerId);
        startActivity(intent);
    }

    public void add_customer(View view) {
        customerListStale = true;
        PendingIntent pendingResult = createPendingResult(
                com.google.zero_touchresellertool.AddCustomer.ADD_CODE, new Intent(), 0);
        Intent intent = new Intent(MainActivity.this, com.google.zero_touchresellertool.AddCustomer.class);
        intent.putExtra(com.google.zero_touchresellertool.AddCustomer.RESELLER_ID, resellerId);
        startActivity(intent);
    }

}