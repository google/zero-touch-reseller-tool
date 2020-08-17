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

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import com.google.zero_touchresellertool.MainActivity;
import com.google.zero_touchresellertool.ZTDevice;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androiddeviceprovisioning.v1.AndroidProvisioningPartner;
import com.google.api.services.androiddeviceprovisioning.v1.model.ClaimDeviceRequest;
import com.google.api.services.androiddeviceprovisioning.v1.model.ClaimDeviceResponse;
import com.google.api.services.androiddeviceprovisioning.v1.model.Company;
import com.google.api.services.androiddeviceprovisioning.v1.model.CreateCustomerRequest;
import com.google.api.services.androiddeviceprovisioning.v1.model.Device;
import com.google.api.services.androiddeviceprovisioning.v1.model.DeviceIdentifier;
import com.google.api.services.androiddeviceprovisioning.v1.model.FindDevicesByOwnerRequest;
import com.google.api.services.androiddeviceprovisioning.v1.model.FindDevicesByOwnerResponse;
import com.google.api.services.androiddeviceprovisioning.v1.model.ListCustomersResponse;
import com.google.api.services.androiddeviceprovisioning.v1.model.UnclaimDeviceRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ApsService extends IntentService {
    private static final String TAG = ApsService.class.getSimpleName();
    public static final String COMMAND_DELETE_DEVICES = "DeleteDevices";
    public static final String DEVICES = "devices";
    public static final String COMMAND_ADD_CUSTOMER = "AddCustomer";
    public static final String CUSTOMER_NAME = "cutomer_name";
    public static final String CUSTOMER_OWNER = "customer_owner";
    public static final String CUSTOMER_ADMINS = "customer_admins";
    public static final int APS_CODE = 0;
    public static final String PENDING_RESULT_EXTRA = "pending_result";
    public static final String COMMAND = "command";
    public static final String COMMMAND_LIST_CUSTOMERS = "ListCustomers";
    public static final String RESPONSE_LIST_CUSTOMERS = "response_customer_list";
    public static final String COMMAND_LIST_DEVICES = "ListDevices";
    public static final String COMMAND_ADD_DEVICE = "AddDevice";
    public static final String ERROR = "error";
    public static final String RESPONSE_DEVICE_LIST_FIRST_PAGE = "device_list_first_page";
    public static final String RESPONSE_DEVICE_LIST_ADDITIONAL_PAGE = "device_list_additional_page";
    public static final String CUSTOMER_RESULT = "customer_result";
    public static final String DEVICE_RESULT_FIRST_PAGE = "device_result_first_page";
    public static final String DEVICE_RESULT_ADDITIONAL_PAGE = "device_result_additional_page";
    public static final String RESELLER_ID = "reseller_id";
    public static final String CUSTOMER_ID = "customer_id";
    public static final String DEVICE = "device";
    public static final Long pagelimit = 100L;
    public static final String ERROR_COMMUNICATION = "communication_error";
    public static final String ERROR_UNKNOWN = "unknown_error";
    public static final String RESPONSE_DELETE_DEVICES = "response_delete_devices";
    public static final String RESPONSE_DELETE_DEVICES_WITH_ERROR = "delete_devices_with_error";
    public static final String ERROR_ADMIN_EMAIL_INVALID = "admin_email_invalid";
    public static final String RESPONSE_ADD_CUSTOMER = "response_add_customer";
    private static String service_account = "";
    public static final String ERROR_INVALID_MANUFACTURER = "Invalid manufacturer";
    public static final String ERROR_RECLAIM = "Precondition check failed";
    public static final String ERROR_ANOTHER_CUSTOMER = "Unable to apply configuration profile to device - you are not the owner";
    public static final String ERROR_BAD_MODEL = "Invalid manufacturer, model combination";
    public static final String ERROR_NOT_GOOGLE_ACCOUNT = "One or more of the supplied email addresses isn't a Google Account. Ensure that there is a Google Account for each email address supplied.";

    private static JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static HttpTransport HTTP_TRANSPORT;
    private static final List<String> SCOPES =
            Collections.singletonList("https://www.googleapis.com/auth/androidworkprovisioning");
    private FirebaseFirestore mDb = null;
    private static String mUsername = "";
    private static boolean enableDb;

    public ApsService() {
        super(TAG);
    }

    public static void setServiceAccount(String service_account) {
        ApsService.service_account = service_account;
    }

    public static void useDb(boolean use) {
        enableDb = use;
    }

    public static void setUsername(String mcUsername) {
        mUsername = mcUsername;
    }

    private AndroidProvisioningPartner authorize() {
        AndroidProvisioningPartner service;
        GoogleCredential credential;

        if (service_account.equals("")) {
            Log.d(TAG, "Authorize called with no service account specified");
            return_error();
            return null;
        }

        try {
            HTTP_TRANSPORT = new com.google.api.client.http.javanet.NetHttpTransport();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }

        try {
            InputStream sa = new ByteArrayInputStream(service_account.getBytes());
            credential = GoogleCredential.fromStream(sa).createScoped(SCOPES);
            service = new AndroidProvisioningPartner.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(getString(R.string.service_app_name))
                    .build();
            sa.close();

        } catch (IOException e) {
            Log.e(TAG, "IOException creating service");
            return_error();
            return null;
        }

        if (enableDb) {
            mDb = FirebaseFirestore.getInstance();
        }

        return service;
    }

    private void list_customers(AndroidProvisioningPartner service, long resellerId) {
        if (resellerId == 0) {
            Log.d(TAG, "No reseller ID passed");
            return;
        }

        // Send an API request to list all our customers.
        AndroidProvisioningPartner.Partners.Customers.List request;
        try {
            request = service.partners().customers().list(resellerId);
        } catch (IOException e) {
            Log.e(TAG, "IOException creating service request");
            return_error();
            return;
        }
        ListCustomersResponse response;
        try {
            response = request.execute();
        } catch (IOException e) {
            Log.e(TAG, "IOException executing service request");
            return_error();
            return;
        }
        auditLog(COMMMAND_LIST_CUSTOMERS, resellerId);

        if (response.getCustomers() != null) {
            List<Company> customers = response.getCustomers();
            Intent retIntent = new Intent(MainActivity.RETURN_RESULT);
            retIntent.putExtra(COMMAND, RESPONSE_LIST_CUSTOMERS);
            retIntent.putExtra(CUSTOMER_RESULT, (Serializable) customers);
            LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
            return;
        } else {
            Log.d(TAG, "No customers found");
            return;
        }
    }

    private void auditLog(final String command, long resellerId) {
        if (mDb != null) {
            Map<String,Object> log = new HashMap<>();
            DateFormat df = DateFormat.getDateTimeInstance();
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            log.put("Timestamp", df.format(new Date()).toString());
            log.put("Action", command);
            log.put("Reseller ID", resellerId);
            if (!mUsername.equals("")) {
                log.put( "Username", mUsername);
            }
            mDb.collection("auditlog")
                    .add(log)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Failed to add to audit log, command = " + command);
                        }
                    });
        }
    }

    private void auditLog(final String command, long resellerId, long customerId) {
        if (mDb != null) {
            Map<String,Object> log = new HashMap<>();
            DateFormat df = DateFormat.getDateTimeInstance();
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            log.put("Timestamp", df.format(new Date()).toString());
            log.put("Action", command);
            log.put("Reseller ID", resellerId);
            log.put( "Customer ID", customerId);
            if (!mUsername.equals("")) {
                log.put( "Username", mUsername);
            }
            mDb.collection("auditlog")
                    .add(log)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Failed to add to audit log, command = " + command);
                        }
                    });
        }
    }

    private void auditLog(final String command, long resellerId, String customerName, String customerOwner, String customerAdmins) {
        if (mDb != null) {
            Map<String,Object> log = new HashMap<>();
            DateFormat df = DateFormat.getDateTimeInstance();
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            log.put("Timestamp", df.format(new Date()).toString());
            log.put("Action", command);
            log.put("Reseller ID", resellerId);
            log.put( "Customer name", customerName);
            log.put( "Customer owner", customerOwner);
            log.put( "Customer admins", customerAdmins);
            if (!mUsername.equals("")) {
                log.put( "Username", mUsername);
            }
            mDb.collection("auditlog")
                    .add(log)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Failed to add to audit log, command = " + command);
                        }
                    });
        }
    }

    private void auditLog(final String command, long resellerId, List<ZTDevice> devices) {
        if (mDb != null && devices != null) {
            for (ZTDevice d : devices) {
                Map<String, Object> log = new HashMap<>();
                DateFormat df = DateFormat.getDateTimeInstance();
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                log.put("Timestamp", df.format(new Date()).toString());
                log.put("Action", command);
                log.put("Reseller ID", resellerId);
                log.put("Device ID", d.getDeviceId());
                DeviceIdentifier id = d.getDeviceIdentifier();
                log.put("Device manufacturer", id.getManufacturer());
                if (id.getImei() != null) {
                    log.put("Device IMEI", id.getImei());
                }
                if (id.getMeid() != null) {
                    log.put("Device MEID", id.getMeid());
                }
                if (id.getSerialNumber() != null) {
                    log.put("Device serial number", id.getSerialNumber());
                }
                if (id.getModel() != null) {
                    log.put("Device model", id.getModel());
                }
                if (!mUsername.equals("")) {
                    log.put("Username", mUsername);
                }
                mDb.collection("auditlog")
                        .add(log)
                        .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                            @Override
                            public void onSuccess(DocumentReference documentReference) {
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Failed to add to audit log, command = " + command);
                            }
                        });
            }
        }
    }

    private void list_devices(AndroidProvisioningPartner service, long resellerId, long customerId) {
        if (resellerId == 0) {
            Log.d(TAG, "No partner ID passed");
            return_error();
        }
        if (customerId == 0) {
            Log.d(TAG, "No customer ID passed");
            return_error();
        }

        Log.d(TAG, "Find devices for customer with ID: " + customerId);
        auditLog(COMMAND_LIST_DEVICES, resellerId, customerId);

        // Send an API request to list all our customers.
        AndroidProvisioningPartner.Partners.Devices.FindByOwner request;
        FindDevicesByOwnerRequest owner = new FindDevicesByOwnerRequest();
        List<Long> customers = Collections.singletonList(customerId);
        owner.setCustomerId(customers);
        owner.setSectionType("SECTION_TYPE_ZERO_TOUCH");
        owner.setLimit(pagelimit);
        boolean moretodo = true;
        boolean firsttime = true;
        List<Device> devices = Collections.emptyList();
        while (moretodo) {
            try {
                request = service.partners().devices().findByOwner(resellerId, owner);
            } catch (IOException e) {
                Log.e(TAG, "IOException creating find by owner request");
                return_error();
                return;
            }
            FindDevicesByOwnerResponse response;
            try {
                response = request.execute();
            } catch (IOException e) {
                Log.e(TAG, "IOException executing find by owner request");
                e.printStackTrace();
                return_error();
                return;
            }
            if (firsttime) {
                devices = response.getDevices();
                Intent retIntent = new Intent(MainActivity.RETURN_RESULT);
                retIntent.putExtra(COMMAND, RESPONSE_DEVICE_LIST_FIRST_PAGE);
                retIntent.putExtra(DEVICE_RESULT_FIRST_PAGE, (Serializable) devices);
                LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
                firsttime = false;
                if (devices == null) {
                    moretodo = false;
                }
            } else {
                devices = response.getDevices();
                Intent retIntent = new Intent(MainActivity.RETURN_RESULT);
                retIntent.putExtra(COMMAND, RESPONSE_DEVICE_LIST_ADDITIONAL_PAGE);
                retIntent.putExtra(DEVICE_RESULT_ADDITIONAL_PAGE, (Serializable) devices);
                LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
            }
            if (response.getNextPageToken() == null) {
                moretodo = false;
            }
            owner.setPageToken(response.getNextPageToken());
        }
    }

    void return_error() {
        Intent retIntent = new Intent(MainActivity.RETURN_RESULT);
        retIntent.putExtra(COMMAND, ERROR);
        LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
    }

    void return_error_add_device(String error) {
        Intent retIntent = new Intent(com.google.zero_touchresellertool.AddDevice.RETURN_RESULT);
        retIntent.putExtra(COMMAND, ERROR);
        retIntent.putExtra(ERROR, error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PendingIntent reply = intent.getParcelableExtra(PENDING_RESULT_EXTRA);
        Log.d(TAG, "APSService intent started");
        AndroidProvisioningPartner service = authorize();
        if (service == null) {
            Log.e(TAG, "Error creating service.");
            return;
        }
        try {
            String command = intent.getStringExtra(COMMAND);
            if (command == null) {
                Log.e(TAG,"No comment was send to APSService");
                return;
            } else {
                switch (command) {
                    case COMMMAND_LIST_CUSTOMERS: {
                        long partner_id = intent.getLongExtra(RESELLER_ID, 0);
                        list_customers(service, partner_id);
                        break;
                    }
                    case COMMAND_LIST_DEVICES: {
                        long partner_id = intent.getLongExtra(RESELLER_ID, 0);
                        long customer_id = intent.getLongExtra(CUSTOMER_ID, 0);
                        list_devices(service, partner_id, customer_id);
                        break;
                    }
                    case COMMAND_ADD_DEVICE: {
                        long partner_id = intent.getLongExtra(RESELLER_ID, 0);
                        long customer_id = intent.getLongExtra(CUSTOMER_ID, 0);
                        String deviceAsString = intent.getStringExtra(DEVICE);
                        DeviceIdentifier device = null;
                        if (deviceAsString != null) {
                            Gson gson = new Gson();
                            device = gson.fromJson(deviceAsString, DeviceIdentifier.class);
                        }
                        add_device(service, partner_id, customer_id, device);
                        break;
                    }
                    case COMMAND_DELETE_DEVICES: {
                        long partner_id = intent.getLongExtra(RESELLER_ID, 0);
                        delete_devices(service, partner_id, intent.getParcelableArrayListExtra(DEVICES));
                        break;
                    }
                    case COMMAND_ADD_CUSTOMER: {
                        long partner_id = intent.getLongExtra(RESELLER_ID, 0);
                        String customer_name = intent.getStringExtra(CUSTOMER_NAME);
                        String customer_owner = intent.getStringExtra(CUSTOMER_OWNER);
                        String customer_admins = intent.getStringExtra(CUSTOMER_ADMINS);
                        add_customer(service, partner_id, customer_name, customer_owner, customer_admins);
                        break;
                    }
                }
            }
        } catch (Exception exc) {
            Log.e(TAG, "Exception handling intent in APSService");
            exc.printStackTrace();
        }

    }

    private void add_customer(AndroidProvisioningPartner service, long partner_id, String customer_name, String customer_owner, String customer_admins) {
        Company newCustomer = new Company();
        newCustomer.setCompanyName(customer_name);
        ArrayList<String> owners = new ArrayList<>();
        owners.add(customer_owner);
        newCustomer.setOwnerEmails(owners);
        ArrayList<String> admins = new ArrayList<>();
        if (!customer_admins.equals("")) {
            String[] emails = customer_admins.split(",");
            for (String email : emails) {
                if (!(!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches())) {
                    // error admin must be comma separated emails
                    Log.e(TAG, "Customer admin not an email: "+email);
                    return_error_add_customer(ERROR_ADMIN_EMAIL_INVALID);
                    return;
                } else {
                    String newAdmin = email.trim();
                    // strip any admin emails that are duplicates
                    if (admins.contains(newAdmin) || newAdmin.equals(customer_owner)) {
                        Log.d(TAG, "Discarding duplicate admin email: " + newAdmin);
                    } else {
                        admins.add(newAdmin);
                    }
                }
            }
        }
        newCustomer.setAdminEmails(admins);
        CreateCustomerRequest customerRequest = new CreateCustomerRequest();
        customerRequest.setCustomer(newCustomer);
        try {
            AndroidProvisioningPartner.Partners.Customers.Create create = service.partners().customers().create("partners/"+partner_id, customerRequest);
            create.execute();
        } catch (IOException e) {
            Log.e(TAG, "IOException creating customer: name = " + customer_name + " and owner = " + customer_owner + " and admins = " + customer_admins);
            e.printStackTrace();
            HttpResponseException httpe = (HttpResponseException)e;
            int code;
            String message, status;
            try {
                JSONObject json = new JSONObject(httpe.getContent());
                message = json.getString("message");
            } catch (JSONException j) {
                Log.e(TAG, "Invalid HTTP error returned from device claim");
                return_error_add_customer(ERROR_COMMUNICATION);
                return;
            }
            if (message == null) {
                message = ERROR_UNKNOWN;
            }
            return_error_add_customer(message);
            return;
        }
        auditLog(COMMAND_ADD_CUSTOMER, partner_id, customer_name, customer_owner, customer_admins);
        Intent retIntent = new Intent(com.google.zero_touchresellertool.AddCustomer.RETURN_RESULT);
        retIntent.putExtra(COMMAND, RESPONSE_ADD_CUSTOMER);
        LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
    }

    private void return_error_add_customer(String message) {
        Intent retIntent = new Intent(com.google.zero_touchresellertool.AddCustomer.RETURN_RESULT);
        retIntent.putExtra(COMMAND, ERROR);
        retIntent.putExtra(ERROR, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
    }

    private void delete_devices(AndroidProvisioningPartner service, long partner_id, ArrayList<Parcelable> deletelist) {
        boolean success = true;
        ArrayList<Long> done = new ArrayList<Long>(0);
        ArrayList<ZTDevice> doneLog = new ArrayList<>(0);
        for (Parcelable p : deletelist) {
            ZTDevice d = (ZTDevice) p;
            UnclaimDeviceRequest unclaimRequest = new UnclaimDeviceRequest();
            unclaimRequest.setDeviceId(d.getDeviceId());
            unclaimRequest.setSectionType("SECTION_TYPE_ZERO_TOUCH");
            try {
                AndroidProvisioningPartner.Partners.Devices.Unclaim unclaim = service.partners().devices().unclaim(partner_id, unclaimRequest);
                unclaim.execute();
                done.add(d.getDeviceId());
                doneLog.add(d);
            } catch (IOException e) {
                Log.e(TAG, "IOException unclaiming device: " + d.toString());
                e.printStackTrace();
                success = false;
            }
        }
        if (success) {
            auditLog(COMMAND_DELETE_DEVICES, partner_id, doneLog);
        }
        Intent retIntent = new Intent(MainActivity.RETURN_RESULT);
        retIntent.putExtra(COMMAND, success? RESPONSE_DELETE_DEVICES : RESPONSE_DELETE_DEVICES_WITH_ERROR);
        retIntent.putExtra(DEVICES, done);
        LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
    }

    private void add_device(AndroidProvisioningPartner service, long partner_id, long customer_id, DeviceIdentifier device) {
        Log.d(TAG, "add_device called with partner_id = " + partner_id + " customer_id = " + customer_id + " device = " + device);
        ClaimDeviceRequest claimRequest = new ClaimDeviceRequest();
        claimRequest.setCustomerId(customer_id);
        claimRequest.setDeviceIdentifier(device);
        claimRequest.setSectionType("SECTION_TYPE_ZERO_TOUCH");
        ClaimDeviceResponse response;
        try {
            AndroidProvisioningPartner.Partners.Devices.Claim claim = service.partners().devices().claim(partner_id, claimRequest);
            response = claim.execute();
            Log.d(TAG, "claim device response = " + response.toString());
        } catch (IOException e) {
            Log.e(TAG, "IOException claiming device");
            e.printStackTrace();
            HttpResponseException httpe = (HttpResponseException)e;
            Log.d(TAG, "httpe.getStatusCode() = " + httpe.getStatusCode());
            Log.d(TAG, "httpe.getStatusMessage() = " + httpe.getStatusMessage());
            Log.d(TAG, "httpe.getMessage() = " + httpe.getMessage());
            Log.d(TAG, "https.getContent() = " + httpe.getContent());
            int code;
            String message, status;
            try {
                JSONObject json = new JSONObject(httpe.getContent());
                code = json.getInt("code");
                message = json.getString("message").split("\\.", 2)[0];
                status = json.getString( "status");
                Log.d(TAG, "code = " + code + ", message = " + message + ", status = " + status);
            } catch (JSONException j) {
                Log.e(TAG, "Invalid HTTP error returned from device claim");
                return_error_add_device(ERROR_COMMUNICATION);
                return;
            }
            if (message == null) {
                message = ERROR_UNKNOWN;
            }
            return_error_add_device(message);
            return;
        }
        ZTDevice d = new ZTDevice();
        d.setDeviceIdentifier(device);
        d.setDeviceId(response.getDeviceId());
        List<ZTDevice> addedDevices = new ArrayList<>(1);
        addedDevices.add(d);
        auditLog(COMMAND_ADD_DEVICE, partner_id, addedDevices);
        Intent retIntent = new Intent(com.google.zero_touchresellertool.AddDevice.RETURN_RESULT);
        retIntent.putExtra(COMMAND, COMMAND_ADD_DEVICE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(retIntent);
    }

}
