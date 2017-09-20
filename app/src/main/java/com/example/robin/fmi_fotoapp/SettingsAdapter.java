package com.example.robin.fmi_fotoapp;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;

/**
 * Created by Robin
 */

public class SettingsAdapter extends BaseExpandableListAdapter {

    private List<String> headerTitles;
    private HashMap<String,List<String>> childTitles;
    private Context ctx;
    private class MyTag //this tag is set on a switch to identify the purpose
    {
        String  title;

        public MyTag()
        {
            title = null;

        }

        public MyTag(String title)
        {
                this.title = title;
        }

    }

    SettingsAdapter(Context ctx, List<String> headerTitles, HashMap<String,List<String>> childTitles){
        this.ctx = ctx;
        this.headerTitles = headerTitles;
        this.childTitles = childTitles;
    }

    @Override
    public int getGroupCount() {
        return headerTitles.size(); //number of headerTitles
    }

    @Override
    public int getChildrenCount(int i) {
        return childTitles.get(headerTitles.get(i)).size(); //number of child items in each header
    }

    @Override
    public Object getGroup(int i) {
        return headerTitles.get(i); //return group object at index
    }

    @Override
    public Object getChild(int i, int i1) {
        return childTitles.get(headerTitles.get(i)).get(i1); //return child item at index
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int i, boolean b, View view, ViewGroup viewGroup) {
        String title = (String) this.getGroup(i);

        if(view == null){
            LayoutInflater layoutInflater = (LayoutInflater) this.ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = layoutInflater.inflate(R.layout.setting_parent_layout,null);
        }

        TextView textView = view.findViewById(R.id.settingsHeader);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setText(title);

        return view;
    }

    @Override
    public View getChildView(int i, int i1, boolean b, View view, ViewGroup viewGroup) {
        String title = (String) this.getChild(i, i1);

        if (view == null){
            LayoutInflater layoutInflater = (LayoutInflater) this.ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = layoutInflater.inflate(R.layout.settings_child_layout,null);
        }

        TextView textView = view.findViewById(R.id.settingsLabel);
        textView.setText(title);
        Switch sw = view.findViewById(R.id.settingsSwitch);
        sw.setTag(new MyTag(title));
        sw.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                MyTag swTag = (MyTag) compoundButton.getTag();
                String swTitle = swTag.title;
                if(b) //switch is on...
                {
                    Toast.makeText(ctx, swTitle + "switch was turned on!", Toast.LENGTH_SHORT).show();
                }
                else //switch is off
                {
                    Toast.makeText(ctx, swTitle + "switch was turned off!", Toast.LENGTH_SHORT).show();
                }
            }

        });

        return view;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }
}
