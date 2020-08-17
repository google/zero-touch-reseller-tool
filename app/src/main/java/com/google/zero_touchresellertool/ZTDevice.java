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

import android.os.Parcel;
import android.os.Parcelable;

import com.google.api.services.androiddeviceprovisioning.v1.model.DeviceIdentifier;

public class ZTDevice implements Parcelable {

    private static final String TAG = ZTDevice.class.getSimpleName();
    Long deviceid = 0L;
    String imei = "";
    String meid = "";
    String model = "";
    String serialnumber = "";
    String manufacturer = "";
    boolean isSpecial = false;


    public ZTDevice() {
        deviceid = 0L;
        imei = "";
        meid = "";
        model = "";
        serialnumber = "";
        manufacturer = "";
    }

    protected ZTDevice(Parcel in) {
        if (in.readByte() == 0) {
            deviceid = null;
        } else {
            deviceid = in.readLong();
        }
        imei = in.readString();
        meid = in.readString();
        model = in.readString();
        serialnumber = in.readString();
        manufacturer = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (deviceid == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeLong(deviceid);
        }
        dest.writeString(imei);
        dest.writeString(meid);
        dest.writeString(model);
        dest.writeString(serialnumber);
        dest.writeString(manufacturer);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ZTDevice> CREATOR = new Creator<ZTDevice>() {
        @Override
        public ZTDevice createFromParcel(Parcel in) {
            return new ZTDevice(in);
        }

        @Override
        public ZTDevice[] newArray(int size) {
            return new ZTDevice[size];
        }
    };

    public String toString() {
        if (!manufacturer.equals("")) {
            String imeiout = "";
            String meidout = "";
            String serialnumberout = "";
            String modelout = "";
            if (imei != null) {
                imeiout = " IMEI: "+imei;
            }
            if (meid != null) {
                meidout = " MEID: "+meid;
            }
            if (serialnumber != null) {
                serialnumberout = " Serialnumber: "+serialnumber;
            }
            if (model != null) {
                modelout = " Model: "+model;
            }
            return manufacturer+imeiout+meidout+modelout+serialnumberout;
        } else {
            return "Unintialized device";
        }
    }

    public Long getDeviceId() {
        return deviceid;
    }

    public void setDeviceId(Long d) {
        deviceid = d;
    }

    public DeviceIdentifier getDeviceIdentifier() {
        DeviceIdentifier deviceIdentifier = new DeviceIdentifier();
        deviceIdentifier.setImei(imei);
        deviceIdentifier.setMeid(meid);
        deviceIdentifier.setModel(model);
        deviceIdentifier.setSerialNumber(serialnumber);
        deviceIdentifier.setManufacturer(manufacturer);
        return deviceIdentifier;
    }

    public void setDeviceIdentifier(DeviceIdentifier d) {
        imei = d.getImei();
        meid = d.getMeid();
        model = d.getModel();
        serialnumber = d.getSerialNumber();
        manufacturer = d.getManufacturer();
    }

}