package com.example.robin.fmi_fotoapp;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;

/**
 * Created by Robin
 */

public class SettingsAdapter extends BaseExpandableListAdapter {

    //2 different child types slide and number
    private static final int CHILD_TYPE_1 = 0;
    private static final int CHILD_TYPE_2 = 1;
    private static final int CHILD_TYPE_UNDEFINED = 3; //undefined type

    //2 different group types triggers and other
    private static final int GROUP_TYPE_1 = 0;
    private static final int GROUP_TYPE_2 = 1;


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
        int childType = getChildType(i, i1);

        if (view == null){
            LayoutInflater layoutInflater = (LayoutInflater) this.ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            switch (childType) {
                case CHILD_TYPE_1:
                    view = layoutInflater.inflate(R.layout.settings_child_layout, null);
                    break;
                case CHILD_TYPE_2:
                    view = layoutInflater.inflate(R.layout.settings_child_layout_2, null);
                    break;
                case CHILD_TYPE_UNDEFINED:
                    view = layoutInflater.inflate(R.layout.settings_child_layout_undefined, null);
                    break;
                default:
                    // Maybe we should implement a default behaviour but it should be ok we know there are 2 child types right?
                    break;
            }
        }

        TextView textView = view.findViewById(R.id.settingsLabel);
        textView.setText(title);

        switch (childType){
            case CHILD_TYPE_1:
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
                break;
            case CHILD_TYPE_2:
                EditText numberField = view.findViewById(R.id.settingsNumber);
                numberField.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        //This method is called to notify you that, within charSequence, the i1 characters beginning at i are about to be replaced by new text with length i2.
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                        //This method is called to notify you that, within charSequence, the i2 characters beginning at i have just replaced old text that had length i1.
                    }

                    @Override
                    public void afterTextChanged(Editable editable) { //This method is called to notify you that, somewhere within editable, the text has been changed.
                        Toast.makeText(ctx, "delay is now: " + editable.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case CHILD_TYPE_UNDEFINED:
                Toast.makeText(this.ctx,  "undefined child!", Toast.LENGTH_SHORT).show();
                break;
        }


        return view;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }

    @Override
    public int getChildType(int groupPosition, int childPosition) {
        switch (groupPosition) {
            case 0:
                return CHILD_TYPE_1; //group 1 consists of sliders only
            case 1:
                return CHILD_TYPE_2; // at the moment group 2 only contains a field to set the trigger delay
            default:
                return CHILD_TYPE_UNDEFINED; //else undefined
        }
    }

    @Override
    public int getChildTypeCount() {
        return 2; //2 types of rows in the settings menu
    }

    @Override
    public int getGroupType(int groupPosition) {
        switch (groupPosition) {
            case 0:
                return GROUP_TYPE_1;
            case 1:
                return GROUP_TYPE_2;
            default:
                return GROUP_TYPE_1;
        }
    }

    @Override
    public int getGroupTypeCount() {
        return 2; //2 types of groups in the settings menu
    }
}
