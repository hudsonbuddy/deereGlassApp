package com.repco.deere.glass;

import com.repco.deere.glass.base.BaseBoundActivity;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

public class AlertsMenuActivity extends BaseBoundActivity {
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.live_card_menu, menu);
		return true;
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		finish();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.resume_mi:
			Intent menuIntent = new Intent(this, AlertViewActivty.class);
			menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(menuIntent);
			break;
		case R.id.stop_service_mi:
			mAlertsBinder.stop();
			break;
		default:
			return false;
		}
		closeOptionsMenu();
		return true;

	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		openOptionsMenu();
	}


	@Override
	protected void onUnBind() {	
	}

	@Override
	protected void onBind(boolean alreadyConnected) { }

}
