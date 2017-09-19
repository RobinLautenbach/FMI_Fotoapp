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

    ExpandableListView listView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_einstellungen);
        listView = (ExpandableListView) findViewById(R.id.settingsListView);
        List<String> sectionHeadings = new ArrayList<String>(); //list of setting section headers
        List<String> triggerList = new ArrayList<String>(); //list of camera triggers
        List<String> otherHeadings = new ArrayList<String>(); //list of other settings section
        HashMap<String,List<String>> childList = new HashMap<String,List<String>>();
        String triggerHeadingItems[] = getResources().getStringArray(R.array.settingsHeader); //get section headers
        String triggers[] = getResources().getStringArray(R.array.triggerChilds); //get section items
        for(String title : triggerHeadingItems){
            sectionHeadings.add(title); //add title to setting section list
        }
        for(String title : triggers) {
            triggerList.add(title); //add title to the section child list
        }
        childList.put(sectionHeadings.get(0),triggerList); //add first settings section to the map
        final SettingsAdapter adapter = new SettingsAdapter(this, sectionHeadings, childList);
        listView.setAdapter(adapter);

    }

}
