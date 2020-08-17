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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.zero_touchresellertool.ApsService;
import com.google.zero_touchresellertool.MainActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class AddCustomer extends AppCompatActivity {
    public static final int ADD_CODE = 3;
    private static final String TAG = AddCustomer.class.getSimpleName();
    public static String RESELLER_ID = "reseller_id";
    public static String CUSTOMER_ID = "customer_id";
    private static Long reseller_id = 0L;
    static final String RETURN_RESULT = "AddCustomerReturn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_customer);

        Intent intent = getIntent();
        reseller_id = intent.getLongExtra(RESELLER_ID, 0L);
        Log.d(TAG, "Started AddCustomer with partner_id = " + reseller_id);
    }

    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra(ApsService.COMMAND);
            if (command == null) {
                Log.e(TAG, "No command given to AddCustomer broadcast receiver");
                return;
            }
            switch (command) {
                case ApsService.RESPONSE_ADD_CUSTOMER: {
                    Toast toast = Toast.makeText(getApplicationContext(), R.string.customer_added_success, Toast.LENGTH_SHORT);
                    toast.show();
                    Intent retIntent = new Intent(MainActivity.RETURN_RESULT);
                    retIntent.putExtra(ApsService.COMMAND, ApsService.RESPONSE_ADD_CUSTOMER);
                    LocalBroadcastManager.getInstance(AddCustomer.this).sendBroadcast(retIntent);
                    Intent maintent = new Intent(AddCustomer.this,
                            MainActivity.class);
                    startActivity(maintent);
                    break;
                }
                case ApsService.ERROR: {
                    String error = intent.getStringExtra(ApsService.ERROR);
                    String toastmessage = getString(R.string.error_message);
                    if (error != null) {
                        switch (error) {
                            case ApsService.ERROR_NOT_GOOGLE_ACCOUNT: {
                                toastmessage = getString(R.string.error_not_google_account);
                                break;
                            }
                            case ApsService.ERROR_ADMIN_EMAIL_INVALID: {
                                toastmessage = getString(R.string.error_admin_email_invalid);
                                break;
                            }
                            case ApsService.ERROR_COMMUNICATION: {
                                toastmessage = getString(R.string.error_communication);
                                break;
                            }
                            default: {
                                toastmessage = error;
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

    public void create_customer(View view) {
        EditText customer_name = findViewById(R.id.new_customer_name);
        EditText customer_owner = findViewById(R.id.new_customer_owner);
        EditText customer_admins = findViewById(R.id.new_customer_admins);

        if (customer_name.getText().toString().equals("")) {
            // error need customer name
            Log.e(TAG, "No customer name");
        }
        if (!(!TextUtils.isEmpty(customer_owner.getText()) && Patterns.EMAIL_ADDRESS.matcher(customer_owner.getText()).matches())) {
            // error owner must be an email address
            Log.e(TAG, "Customer owner not an email: " + customer_owner.getText());
        }
        if (!customer_admins.getText().equals("")) {
            String emails = customer_admins.getText().toString();
            while (!emails.equals("")) {
                String[] parts = emails.split(",");
                if (!(!TextUtils.isEmpty(parts[0]) && Patterns.EMAIL_ADDRESS.matcher(parts[0]).matches())) {
                    // error admin must be comma separated emails
                    Log.e(TAG, "Customer admin not an email: "+parts[0]);
                }
                if (parts.length >1) {
                    emails = parts[1].trim();
                } else {
                    emails = "";
                }
            }
        }
        Log.d(TAG, "Going to create customer '" + customer_name.getText() + "' with manager " + customer_owner.getText() + " and admins " + customer_admins.getText());
        PendingIntent pendingResult = createPendingResult(
                ApsService.APS_CODE, new Intent(), 0);
        Intent intent = new Intent(getApplicationContext(), ApsService.class);
        intent.putExtra(ApsService.COMMAND, ApsService.COMMAND_ADD_CUSTOMER);
        intent.putExtra(ApsService.PENDING_RESULT_EXTRA, pendingResult);
        intent.putExtra(ApsService.RESELLER_ID, reseller_id);
        intent.putExtra(ApsService.CUSTOMER_NAME, customer_name.getText().toString());
        intent.putExtra(ApsService.CUSTOMER_OWNER, customer_owner.getText().toString());
        intent.putExtra(ApsService.CUSTOMER_ADMINS, customer_admins.getText().toString());
        Log.d(TAG, "Starting service to add customer");
        startService(intent);
    }
}
