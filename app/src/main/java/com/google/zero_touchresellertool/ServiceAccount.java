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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.zero_touchresellertool.ApsService;
import com.google.zero_touchresellertool.MainActivity;

import androidx.appcompat.app.AppCompatActivity;

public class ServiceAccount extends AppCompatActivity {
    public static final String MC_PARTNER_ID = "mc_partner_id";
    public static final String MC_SERVICE_ACCOUNT = "mc_service_account";
    private static final String TAG = ApsService.class.getSimpleName();
    private ServiceAccount ServiceAccount;
    private static boolean mc_reseller_id;
    private static boolean mc_service_account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.service_account);
        TextView sa_textview = findViewById(R.id.sa_entry);
        TextView id_textview = findViewById(R.id.id_entry);
        Context context = ServiceAccount.this;
        Intent intent = getIntent();
        mc_reseller_id = intent.getBooleanExtra(MC_PARTNER_ID, false);
        mc_service_account = intent.getBooleanExtra(MC_SERVICE_ACCOUNT, false);
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        if (mc_service_account) {
            sa_textview.setText(getString(R.string.configured_by_admin));
            sa_textview.setEnabled(false);
        } else {
            sa_textview.setText(sharedPref.getString(getString(R.string.service_account_pref_key), ""));
        }
        if (mc_reseller_id) {
            id_textview.setText(getString(R.string.configured_by_admin));
            id_textview.setEnabled(false);
        } else {
            id_textview.setText(Long.toString(sharedPref.getLong("reseller_id", 0L)));
        }
    }

    public void save_service_account(View view) {
        TextView sa_textview = findViewById(R.id.sa_entry);
        TextView id_textview = findViewById(R.id.id_entry);
        Context context = ServiceAccount.this;
        SharedPreferences sharedPref = context.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        if (!mc_service_account) {
            editor.putString(getString(R.string.service_account_pref_key), sa_textview.getText().toString());
        }
        if (!mc_reseller_id) {
            editor.putLong("reseller_id", Long.parseLong(id_textview.getText().toString()));
        }
        editor.apply();
        Intent maintent = new Intent(ServiceAccount.this,
                MainActivity.class);
        startActivity(maintent);
    }
}
