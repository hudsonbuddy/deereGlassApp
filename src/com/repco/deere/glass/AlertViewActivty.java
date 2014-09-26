package com.repco.deere.glass;

import java.io.File;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import android.animation.LayoutTransition;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.GridView;

import com.google.android.glass.media.CameraManager;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.google.gson.Gson;
import com.repco.deere.glass.AlertService.AlertTypes;
import com.repco.deere.glass.base.AlertDatum;
import com.repco.deere.glass.base.BaseBoundActivity;
import com.repco.deere.glass.base.GridAdapter;

public class AlertViewActivty extends BaseBoundActivity {

	// more negative is more lean back
	private static final float TOP_THRESH = (float) -3.8;

	// less negative is more lean forward
	private static final float BOTTOM_THRESH = (float) -3.6;

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			Gson gson = new Gson();
			AlertDatum alert = gson.fromJson(
					intent.getStringExtra("alertData"), AlertDatum.class);

			System.out.println("Receive Broadcast: " + alert.toString());

			alertStack.push(alert);
		}
	};

	private final Map<AlertTypes, View> viewCache = new HashMap<AlertService.AlertTypes, View>();

	@SuppressWarnings("serial")
	private final Stack<AlertDatum> alertStack = new Stack<AlertDatum>() {

		private void updateView() {

			if (isEmpty()) {
				outer.removeAllViews();
				outer.addView(noAlertsView);
				noAlertsView.requestFocus();
				if (wl.isHeld()) {
					wl.release();
					getWindow().clearFlags(
							WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				}
				return;
			}
			AlertDatum alert = alertStack.peek();
			if (!wl.isHeld()) {
				wl.acquire();
			}
			getWindow()
					.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

			UIDataBindings.buildAlertColorView(alertColorView, alert);

			if (!topmode) {
				// only update view if we're in bottom mode
				outer.removeAllViews();
				outer.addView(alertColorView);
				alertColorView.requestFocus();
			}

		}

		@Override
		public AlertDatum pop() {
			synchronized (this) {
				AlertDatum d = super.pop();
				updateView();
				return d;
			}

		};

		@Override
		public AlertDatum push(AlertDatum alert) {
			synchronized (this) {

				AlertDatum d = super.push(alert);
				updateView();
				return d;
			}
		};
	};
	private View noAlertsView;
	private View alertColorView;
	private GestureDetector mGestureDetector;

	private final GestureDetector.BaseListener gListen = new GestureDetector.BaseListener() {

		@Override
		public boolean onGesture(Gesture gesture) {
			System.out.println("Gesture " + gesture);

			switch (gesture) {
			case SWIPE_LEFT:
			case SWIPE_RIGHT:
				try {
					alertStack.pop();
					mAudio.playSoundEffect(Sounds.DISMISSED);
				} catch (EmptyStackException e) {
					mAudio.playSoundEffect(Sounds.DISALLOWED);
				}
				break;
			case TAP:
				openOptionsMenu();
				break;
			default:
				return false;
			}
			return true;
		}
	};

	private AudioManager mAudio;
	private PowerManager mPm;
	private PowerManager.WakeLock wl;
	private ViewGroup outer;
	private boolean topmode = true;
	private final SensorEventListener mSensorListener = new SensorEventListener() {
		private float lastVal = BOTTOM_THRESH;

		@Override
		public void onSensorChanged(SensorEvent event) {
			float current = event.values[2];
			if (current < TOP_THRESH && lastVal >= TOP_THRESH) {
				goTopMode();

			} else if (current > BOTTOM_THRESH && lastVal <= BOTTOM_THRESH) {
				goBottomMode();
			}
			lastVal = current;
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};

	private void goBottomMode() {
		synchronized (alertStack) {
			if (topmode) {
				topmode = false;
				System.out.println("BOTTOM MODE");
				outer.removeAllViews();
				if (alertStack.size() > 0) {
					UIDataBindings.buildAlertColorView(alertColorView, alertStack.peek());
					outer.addView(alertColorView);
					alertColorView.requestFocus();

				} else {
					outer.addView(noAlertsView);
					noAlertsView.requestFocus();
				}

			}
		}
	}

	private void goTopMode() {
		synchronized (alertStack) {
			if (!topmode) {
				AlertDatum alert;
				try {
					alert = alertStack.peek();
				} catch (EmptyStackException e) {
					mAudio.playSoundEffect(Sounds.DISALLOWED);
					return;
				}
				View view = viewCache.get(alert.type);
				if (view == null) {
					int resId = alert.getResourceId(AlertViewActivty.this);
					view = getLayoutInflater().inflate(resId, null);
					viewCache.put(alert.type, view);
				}
				UIDataBindings.buildAlertView(view, alert);

				topmode = true;
				if (alertStack.size() > 0) {
					outer.removeAllViews();
					outer.addView(view);
					view.requestFocus();
				}

			}
		}
	}

	private SensorManager mSensorManager;
	private Sensor mSensor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

		mGestureDetector = new GestureDetector(this);
		mGestureDetector.setBaseListener(gListen);
		mAudio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		mPm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = mPm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
				| PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "jdeere");

		outer = new FrameLayout(this);
		LayoutTransition lt = new LayoutTransition();
		outer.setLayoutTransition(lt);

		noAlertsView = getLayoutInflater().inflate(R.layout.no_alerts, null);
		alertColorView = getLayoutInflater().inflate(
				R.layout.alert_color_layout, null);
		GridView gridview = (GridView) alertColorView
				.findViewById(R.id.gridview);
		gridview.setAdapter(new GridAdapter(AlertViewActivty.this));
		goBottomMode();

		LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver,
				new IntentFilter(AlertService.alertIntentName));
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (mGestureDetector != null) {
			return mGestureDetector.onMotionEvent(event);
		}
		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(mSensorListener, mSensor,
				SensorManager.SENSOR_DELAY_UI);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(mSensorListener);
		if (wl.isHeld()) {
			wl.release();
		}
	}

	@Override
	protected void onDestroy() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
		super.onDestroy();
	}

	@Override
	protected void onBind(boolean alreadyConnected) {
		setContentView(outer);
		mAlertsBinder.startLiveFeed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.alert_view_menu, menu);
		return true;
	}

	private AlertDatum picAlert;

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.get_status_mi:
			Intent intent = new Intent(this, StatusViewActivity.class);
			startActivity(intent);
			break;
		case R.id.report_alert_mi:
			if (alertStack.empty()) {
				mAlertsBinder.playSound(Sounds.DISALLOWED);
			}
			try {
				picAlert = alertStack.peek();
				Intent picIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				startActivityForResult(picIntent, TAKE_PICTURE_REQUEST);
			} catch (EmptyStackException e) {
				mAlertsBinder.playSound(Sounds.DISALLOWED);
			}
			break;
		default:
			return false;
		}
		return true;
	}

	@Override
	protected void onUnBind() {
		if (mAlertsBinder != null) {
			mAlertsBinder.stopLiveFeed();
		}
	}

	// Begin picture stuff

	private static final int TAKE_PICTURE_REQUEST = 1;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
			String picturePath = data
					.getStringExtra(CameraManager.EXTRA_PICTURE_FILE_PATH);
			processPictureWhenReady(picturePath);
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void processPictureWhenReady(final String picturePath) {
		final File pictureFile = new File(picturePath);

		if (pictureFile.exists()) {
			// The picture is ready; process it.
			String path = pictureFile.getAbsolutePath();
			String extension = MimeTypeMap.getFileExtensionFromUrl(path);
			mAlertsBinder.reportAlert(picAlert, path, MimeTypeMap
					.getSingleton().getMimeTypeFromExtension(extension));

		} else {
			// The file does not exist yet. Before starting the file observer,
			// you
			// can update your UI to let the user know that the application is
			// waiting for the picture (for example, by displaying the thumbnail
			// image and a progress indicator).

			final File parentDirectory = pictureFile.getParentFile();
			FileObserver observer = new FileObserver(parentDirectory.getPath(),
					FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
				// Protect against additional pending events after CLOSE_WRITE
				// or MOVED_TO is handled.
				private boolean isFileWritten;

				@Override
				public void onEvent(int event, String path) {
					if (!isFileWritten) {
						// For safety, make sure that the file that was created
						// in
						// the directory is actually the one that we're
						// expecting.
						File affectedFile = new File(parentDirectory, path);
						isFileWritten = affectedFile.equals(pictureFile);

						if (isFileWritten) {
							stopWatching();

							// Now that the file is ready, recursively call
							// processPictureWhenReady again (on the UI thread).
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									processPictureWhenReady(picturePath);
								}
							});
						}
					}
				}
			};
			observer.startWatching();
		}
	}
}
