package com.repco.deere.glass.base;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import com.repco.deere.glass.AlertService;
import com.repco.deere.glass.R;
import com.repco.deere.glass.AlertService.AlertsBinder;

public abstract class BaseBoundActivity extends Activity{
	protected AlertsBinder mAlertsBinder;
	
	protected ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			finish();
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mAlertsBinder = (AlertsBinder) service;
			if(!mAlertsBinder.hintReady()){
				setContentView(R.layout.connecting);
			}
		}
	};
	
	private final BroadcastReceiver mInitReceiver = new BroadcastReceiver(){
		private boolean mConnected = false;
		@Override
		public void onReceive(Context context, Intent intent) {
			System.out.println("mInitReceiver rcv connected="+mConnected+" "+getClass().getCanonicalName());
			onBind(mConnected);
			mConnected = true;
			
		}
	};
	
	private final BroadcastReceiver mStopReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent) {
			System.out.println("mDeathReceiver rcv "+getClass().getCanonicalName());
			finish();
		};
	};
	
	protected abstract void onBind(boolean alreadyConnected);
	protected abstract void onUnBind();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LocalBroadcastManager.getInstance(this).registerReceiver(mInitReceiver, new IntentFilter(AlertService.serviceReadyIntent));
		LocalBroadcastManager.getInstance(this).registerReceiver(mStopReceiver, new IntentFilter(AlertService.serviceStopIntent));
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mInitReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mStopReceiver);
	}
	@Override
	protected void onStart() {
		super.onStart();
		System.out.println("Binding service");
		bindService(new Intent(this, AlertService.class),
				mServiceConnection, Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE);
		System.out.println("Starting service");
		startService(new Intent(BaseBoundActivity.this, AlertService.class));
	};

	@Override
	protected void onStop() {
		super.onStop();
		onUnBind();
		unbindService(mServiceConnection);
	}
}
