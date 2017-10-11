package com.example.robin.fmi_fotoapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ExpandableListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Robin
 */

public class Einstellungen extends AppCompatActivity {

    private ExpandableListView listView;
    private SettingsAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        listView = (ExpandableListView) findViewById(R.id.settingsListView);
        List<String> sectionHeadings = new ArrayList<String>(); //list of setting section headers
        List<String> triggerList = new ArrayList<String>(); //list of camera triggers
        List<String> otherHeadings = new ArrayList<String>(); //list of other settings section
        HashMap<String,List<String>> childList = new HashMap<String,List<String>>();
        String triggerHeadingItems[] = getResources().getStringArray(R.array.settingsHeader); //get section headers
        String triggers[] = getResources().getStringArray(R.array.triggerChilds); //get section items
        String other[] = getResources().getStringArray(R.array.otherSettingsChilds);
        for(String title : triggerHeadingItems){
            sectionHeadings.add(title); //add title to setting section list
        }
        for(String title : triggers) {
            triggerList.add(title); //add title to the section child list
        }
        for(String title : other) {
            otherHeadings.add(title);
        }
        childList.put(sectionHeadings.get(0),triggerList); //add first settings section to the map
        childList.put(sectionHeadings.get(1),otherHeadings); //add second settings section to the map
        String prefsFile = getResources().getString(R.string.preferenceFile); //name of the SharedPreferences file
        this.adapter = new SettingsAdapter(this, sectionHeadings, childList, prefsFile);
        listView.setAdapter(adapter);

    }

}
