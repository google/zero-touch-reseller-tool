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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

public class CustomerListAdapter extends ArrayAdapter<String> {
    private Context cContext;
    private List<String> customer_list = new ArrayList<>();

    public CustomerListAdapter(Context context, ArrayList<String> list) {
        super(context, 0, list);
        cContext = context;
        customer_list = list;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View listitem = convertView;
        if (listitem == null) {
            listitem = LayoutInflater.from(cContext).inflate(R.layout.customer_list_item,parent,false);
        }

        String current_customer = customer_list.get(position);

        TextView customer_name = (TextView)listitem.findViewById(R.id.customer_name);
        customer_name.setText(current_customer);

        return listitem;
    }
}
