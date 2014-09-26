package com.repco.deere.glass.base;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.Space;
import android.widget.TextView;

public class GridAdapter extends BaseAdapter {
	private Context mContext;
	private int[] mColumnData;

	public GridAdapter(Context c) {
		mContext = c;
		mColumnData = new int[0];
	}

	public int getCount() {

		return mColumnData.length;
	}
	
	public void putRowData(int[] columnData){
		
		mColumnData = columnData;

		
	}
	

	@Override
	public Object getItem(int position) {

		return null;
	}

	@Override
	public long getItemId(int position) {

		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view;

		if (convertView == null) {
			view = new TextView(mContext);
            view.setLayoutParams(new GridView.LayoutParams(11, 50));
		} else {
			view = (View) convertView;
		}
		if(mColumnData[position] == 0){
			view.setBackgroundColor(Color.rgb(0x99, 0xcc, 0x33));

		}else if(mColumnData[position] >0){
			view.setBackgroundColor(Color.rgb(0xcc, 0x33, 0x33));
		}
		return view;
	}
}