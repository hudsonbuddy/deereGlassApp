package com.repco.deere.glass;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;
import com.google.gson.Gson;
import com.repco.deere.glass.AlertsMenuActivity;
import com.repco.deere.glass.R;
import com.repco.deere.glass.base.AlertDatum;
import com.repco.deere.glass.storage.DBSyncHelper;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.RemoteViews;

public class AlertService extends Service {
	private LiveCard mLiveCard;
	private RemoteViews mDashView;

	public static enum AlertTypes {
	DF_MARGIN("deadbeef-dead-beef-dead-000000000001"),
		RIDEQUAL("deadbeef-dead-beef-dead-000000000002"), 
		COV("deadbeef-dead-beef-dead-000000000003"), 
		SINGULATION("deadbeef-dead-beef-dead-000000000004"), 
		ACT_POP("deadbeef-dead-beef-dead-000000000005"); 
		public final UUID uuid;

		private AlertTypes(String uuid) {
			this.uuid = UUID.fromString(uuid);
		}
	}

	public static final AlertTypes findAlertTypeByUUID(UUID uuid) {
		for (AlertTypes t : AlertTypes.values()) {
			if (t.uuid.compareTo(uuid) == 0) {
				return t;
			}
		}
		throw new RuntimeException("GATT CHARACTERISTIC UUID " + uuid
				+ " cannot be mapped to an alert type");
	}

	private void showDash() {
		UIDataBindings.buildLiveCardView(mDashView,alertBuffer.size());
		mLiveCard.setViews(mDashView);
	}

	public static final String alertIntentName = "com.repco.deere.glass.AlertIntent";
	public static final String statusIntentName = "com.repco.deere.glass.StatusIntent";
	public static final String serviceReadyIntent = "com.repco.deere.glass.ServiceReadyIntent";
	public static final String serviceStopIntent = "com.repco.deere.glass.ServiceStopIntent";
	public void showAlert(String intentName, AlertDatum alertDatum) {
		
		Intent intent = new Intent(intentName);
		Gson gson = new Gson();

		intent.putExtra("alertData", gson.toJson(alertDatum));

		if(intentName.equals(statusIntentName)){
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
		else if(intentName.equals(alertIntentName)){
			if(broadcastAlerts){
				LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
			}else{
				alertBuffer.add(alertDatum);
				showDash();
			}
		}
	}

	private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			if ("GlassDeere".equals(device.getName())) {
				mBtAdapter.stopLeScan(mLeScanCallback);

				mBtGatt = device.connectGatt(AlertService.this, false,
						mGattCallback);
			}
		}
	};
	private Map<AlertTypes, BluetoothGattCharacteristic> mCharMap;
	private boolean mConnected = false;
	
	
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		@Override
		public void onConnectionStateChange(
				android.bluetooth.BluetoothGatt gatt, int status, int newState) {

			switch (newState) {
			case BluetoothProfile.STATE_CONNECTED:
				mBtGatt.discoverServices();
				break;
			case BluetoothProfile.STATE_DISCONNECTED:
				mBinder.stop();
				break;
			default:
				break;
			}
		};

		private BluetoothGattService mGattService = null;
		private Queue<BluetoothGattDescriptor> initList = null;

		
		@Override
		public void onServicesDiscovered(android.bluetooth.BluetoothGatt gatt,
				int status) {
			mGattService = null;
			for (BluetoothGattService s : gatt.getServices()) {

				System.out.println("GATTSERVICE: " + s.getUuid());
				if (s.getUuid().compareTo(LEUUID) == 0) {
					mGattService = s;
					break;
				}
			}
			mCharMap = new HashMap<AlertTypes, BluetoothGattCharacteristic>();
			initList = new LinkedBlockingQueue<BluetoothGattDescriptor>();

			if (mGattService == null) {
				System.err.println("Could not find GATT service");
				mAudio.playSoundEffect(Sounds.ERROR);
				stopSelf();
			} else {
				System.out.println("Found GATT service");
				for (AlertTypes t : AlertTypes.values()) {
					BluetoothGattCharacteristic mChar = mGattService
							.getCharacteristic(t.uuid);
					mBtGatt.setCharacteristicNotification(mChar, true);
					System.out.println("CHARACTERISTIC: " + mChar.getUuid());

					BluetoothGattDescriptor desc = null;

					for (BluetoothGattDescriptor d : mChar.getDescriptors()) {
						if (d.getCharacteristic().getUuid()
								.compareTo(mChar.getUuid()) == 0) {
							System.out.println("Using desc " + d.getUuid());
							desc = d;
							break;
						}
						System.out.println("DESC " + d.getUuid() + " -- "
								+ d.getPermissions());
					}
					if (desc == null) {
						throw new RuntimeException(
								"Couldn't get descriptor for UUID "
										+ mChar.getUuid());
					}
					System.out.println("Using desc " + desc.getUuid()
							+ " with perm " + desc.getPermissions());

					desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

					mCharMap.put(t, mChar);

					initList.add(desc);
				}
				mBtGatt.writeDescriptor(initList.poll());
			}
			
		};

		@Override
		public void onDescriptorWrite(BluetoothGatt arg0,
				BluetoothGattDescriptor descriptor, int status) {
			if (status != BluetoothGatt.GATT_SUCCESS) {
				throw new RuntimeException("Could not write descriptor " + descriptor+": fail with status : "+status);
			}
			if (initList.size() > 0) {
				mBtGatt.writeDescriptor(initList.poll());
			} else {
//				mDashView.setTextViewText(R.id.body_text, "connection established");
				mLiveCard.setViews(mDashView);
				mAudio.playSoundEffect(Sounds.SUCCESS);
				mConnected = true;
				showDash();
				sendReadyIntent();
			}

		};

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				android.bluetooth.BluetoothGattCharacteristic characteristic) {
			System.out.println("Characteristic changed: "
					+ characteristic.getUuid());
			mAudio.playSoundEffect(Sounds.SELECTED);
			readQueue.add(new GattReadRequest(characteristic, alertIntentName));
			readNextCharacteristic(false);
		};
		

		private int charRetryCount = 0;
		@Override
		public void onCharacteristicRead(android.bluetooth.BluetoothGatt gatt,
				android.bluetooth.BluetoothGattCharacteristic characteristic,
				int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				charRetryCount = 0;
				GattReadRequest readReq = readQueue.poll();
				System.out.println("onCharacteristicRead "+characteristic.getUuid()+" : "+characteristic.getValue().length+" bytes");
				AlertDatum d = new AlertDatum(findAlertTypeByUUID(characteristic.getUuid()), characteristic.getValue());
				showAlert(readReq.intentName, d);

			}else{
				System.err.println("onCharacteristicRead fail "+characteristic+" : status "+status+" retryCount : "+charRetryCount);
				if(charRetryCount > 5){
					throw new RuntimeException("Too many characteristic read retries");
				}
				charRetryCount += 1;
				System.err.println("Retrying...("+charRetryCount+")");
				
			}
			readNextCharacteristic(true);
		};

	};
	private BluetoothGatt mBtGatt;
	private BluetoothAdapter mBtAdapter;
	private AudioManager mAudio;

	private boolean mConnecting = false;
	@Override
	public void onCreate() {
		super.onCreate();

		mConnecting = false;
		syncHelper  = new DBSyncHelper(this,"alerts");
		
		registerReceiver(syncHelper.mReceiver, DBSyncHelper.WifiIntentFilter);
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(syncHelper.mReceiver);
		if (mLiveCard != null && mLiveCard.isPublished()) {
			mLiveCard.unpublish();
			mLiveCard = null;
		}
		if (mBtGatt != null) {
			System.out.println("Disconnect GATT");
			mBtGatt.disconnect();
			mBtGatt.close();
			mBtGatt = null;
		}
		syncHelper.close();
		mBtAdapter.stopLeScan(mLeScanCallback);
		System.out.println("sending serviceStopIntent");
		LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(serviceStopIntent));
		super.onDestroy();
	}
	/*
	 * Begin HTTPUpload stuff
	 */
	private DBSyncHelper syncHelper;
	
	private static final class GattReadRequest{
		public final BluetoothGattCharacteristic characteristic;
		public final String intentName;
		public GattReadRequest(BluetoothGattCharacteristic characteristic, String intentName){
			this.characteristic = characteristic;
			this.intentName = intentName;
		}
		
		@Override
		public String toString(){
			return characteristic.getUuid()+" : "+intentName;
		}
	}
	private Queue<GattReadRequest> readQueue = new LinkedBlockingQueue<GattReadRequest>();
	private boolean isCurrentlyReading = false;
	private synchronized void readNextCharacteristic(boolean isContinuation){
		GattReadRequest req = readQueue.peek();
		if(req == null){
			System.out.println("Empty readQueue");
			isCurrentlyReading = false;
			return;
		}
		if(!isCurrentlyReading || isContinuation){
			isCurrentlyReading = true;
			System.out.println("Initiating read for "+req+" is continuation "+isContinuation+" isCurrentlyReading "+isCurrentlyReading);
			mBtGatt.readCharacteristic(req.characteristic);
		}else{
			System.out.println("In progress read "+req);
		}
	}

	private final Queue<AlertDatum> alertBuffer = new LinkedBlockingQueue<AlertDatum>();
	private boolean broadcastAlerts = false;
	public class AlertsBinder extends Binder {
		
		public boolean hintReady(){
			if(mConnected){
				sendReadyIntent();
				return true;
			}
			return false;
		}
		public void stop() {
			mAudio.playSoundEffect(Sounds.DISMISSED);
			LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent(serviceStopIntent));
			AlertService.this.stopSelf();
		}

		public void startLiveFeed(){
			broadcastAlerts = true;
			AlertDatum d;
			while((d = alertBuffer.poll()) != null){
				showAlert(alertIntentName, d);
			}
		}
		
		public void stopLiveFeed(){
			broadcastAlerts= false;
			alertBuffer.clear();
			showDash();
		}
		public void getStats() {
			while(!mConnected){
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {}
			}
			for (AlertTypes type : AlertTypes.values()) {
				BluetoothGattCharacteristic c = mCharMap.get(type);
				System.out.println("Reading " + type + " from characteristic "
						+ c.getUuid());
				readQueue.add(new GattReadRequest(c,statusIntentName));
			}
			readNextCharacteristic(false);

		}
		
		public void playSound(int resId){
			mAudio.playSoundEffect(resId);
		}
		
		private final Gson mGson = new Gson();
		public void reportAlert(AlertDatum alert,String filePath,String mime){
			syncHelper.queueData(mGson.toJson(alert), filePath, mime);
		}
	}
	
	private void sendReadyIntent(){
		System.out.println("Send ready intent");
		LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(serviceReadyIntent));
	}

	private final AlertsBinder mBinder = new AlertsBinder();

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	public static final UUID LEUUID = UUID
			.fromString("deadbeef-dead-beef-dead-badb100dbeef");

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(!mConnecting){
			mConnecting = true;
			BluetoothManager btm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			mBtAdapter = btm.getAdapter();
			mAudio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			
			mBtAdapter.cancelDiscovery();
			mBtAdapter.stopLeScan(mLeScanCallback);

			if (mDashView == null) {
				mDashView = new RemoteViews(getPackageName(),
						R.layout.alerts_live_card);
				mDashView.setTextViewText(R.id.body_text,
						"Waiting for connection...");
			}
			if (mLiveCard == null) {
				mLiveCard = new LiveCard(this, "jd-alerts");
				mLiveCard.setDirectRenderingEnabled(false);
	
				
				UIDataBindings.buildConnectingLiveCardView(mDashView);
				
				mLiveCard.setViews(mDashView);
				
				Intent menuIntent = new Intent(this, AlertsMenuActivity.class);

				mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent,
						0));
				// Only has to be done once
				mLiveCard.publish(PublishMode.SILENT);
			}
			System.out.println("Starting LE scan");
	
			mBtAdapter.startLeScan(mLeScanCallback);
		}
		return START_STICKY;
	}

}
