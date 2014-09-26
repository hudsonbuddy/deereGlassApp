package com.repco.deere.glass;

import java.util.LinkedList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.google.gson.Gson;
import com.repco.deere.glass.AlertService.AlertTypes;
import com.repco.deere.glass.base.AlertDatum;
import com.repco.deere.glass.base.BaseBoundActivity;
import com.repco.deere.glass.base.GridAdapter;

public class StatusViewActivity extends BaseBoundActivity{
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {


			Gson gson = new Gson();
			AlertDatum alert = gson.fromJson(
					intent.getStringExtra("alertData"), AlertDatum.class);

			System.out.println("Receive STATUS Broadcast: " + alert.toString());
			
			if(statusList.size() >= AlertTypes.values().length){
				System.err.println("Ignoring status alert update: already have all the items");
				return;
			}			
			
			statusList.add(alert);
			
			if(statusList.size() == AlertTypes.values().length){
				System.out.println("Status update done!");
				View statusView = new StatusScrollView(StatusViewActivity.this);
				setContentView(statusView);
			}

		}
	};
	
	private List<AlertDatum> statusList = new LinkedList<AlertDatum>();
	@Override
	protected void onCreate(android.os.Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(AlertService.statusIntentName));
	};
	
	@Override
	protected void onDestroy() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
		super.onDestroy();
	}
	@Override
	protected void onBind(boolean alreadyConnected) {
		if(!alreadyConnected){
			statusList.clear();
			mAlertsBinder.getStats();
		}
	}
	
	private class StatusScrollView extends CardScrollView{

		private final View[] views;
		public StatusScrollView(Context context) {
			super(context);
			views = new View[statusList.size()];
			int c=0;
			for(AlertDatum alert : statusList){
				views[c] = getLayoutInflater().inflate(alert.getResourceId(StatusViewActivity.this),null);
				UIDataBindings.buildAlertView(views[c], alert);
				c+=1;
			}
			setAdapter(new StatusAdapter());
			activate();
		}
		
		private class StatusAdapter extends CardScrollAdapter{

			@Override
			public int getCount() {
				return views.length;
			}

			@Override
			public Object getItem(int index) {
				return statusList.get(index);
			}

			@Override
			public int getPosition(Object alert) {
				return statusList.indexOf(alert);
			}

			@Override
			public View getView(int position, View recycleView, ViewGroup parent) {
				return views[position];
			}
			
		}
	}

	@Override
	protected void onUnBind() {
		// TODO Auto-generated method stub
		
	}

}
