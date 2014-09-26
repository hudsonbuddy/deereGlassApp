package com.repco.deere.glass.storage;

import java.io.File;

import android.database.Cursor;

public class DBSyncThread {

	private final DBSyncHelper owner;
	private boolean isCancelled = false;
	private final Runnable syncLoop = new Runnable() {

		@Override
		public void run() {
			while (!isCancelled) {
				
				Cursor c = null;
				try {


					c = owner.db.rawQuery("SELECT * FROM " + owner.mTableName
							+ " ORDER BY " + DBSyncHelper.TS_DATA_KEY + " ASC",
							null);
					System.out.println(String.format("Sync thread awake! %d items",c.getCount()));
					while (c.moveToNext()) {

						if (isCancelled) {
							System.out.println("HTTPUploader: cancelled");
							return;
						}

						if (!owner.checkWifi()) {
							System.out
									.println("No wifi... going back to sleep");
							break;
						}

						if (owner.syncRow(c)) {
							int rowId = c
									.getInt(c
											.getColumnIndexOrThrow(DBSyncHelper.ID_KEY));
							System.out.println(String.format(
									"Sync: Removing synced item %d", rowId));
							String filePath = c
									.getString(c
											.getColumnIndexOrThrow(DBSyncHelper.FILE_PATH_KEY));

							System.out.println("Removing " + filePath);

							File f = new File(filePath);
							f.delete();

							int deleted = owner.db.delete(owner.mTableName, String.format("%s=%d", DBSyncHelper.ID_KEY,rowId), null);
							System.out.println(deleted+" items deleted");
							assert(deleted == 1);

							
						}
					}
				} finally {
					if (c != null) {
						c.close();
					}
				}
				try {
					Thread.sleep(DBSyncHelper.SLEEP_TIME_SECONDS * 1000);
				} catch (InterruptedException e) {
					System.out.println("sync sleep interrupted");
				}
			}
			System.out.println("Sync thread is dead!");
		}
	};

	public void cancel() {
		isCancelled = true;
		syncThread.interrupt();
	}

	private final Thread syncThread = new Thread(syncLoop);

	public DBSyncThread(DBSyncHelper owner) {
		this.owner = owner;
	}

	public void poke() {
		syncThread.interrupt();
	}

	public void start() {
		syncThread.start();
	}

}
