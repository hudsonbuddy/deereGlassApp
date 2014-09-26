package com.repco.deere.glass.storage;

import java.io.File;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.Part;
import retrofit.mime.TypedFile;
import retrofit.mime.TypedString;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import com.google.android.gms.auth.GoogleAuthUtil;

public class DBSyncHelper extends SQLiteOpenHelper {

	public static final String DB_NAME = "SyncDB";
	public static final int DB_VERSION = 1;
	
	private static final String SYNC_URL = "http://glassdeere.cloudapp.net/api/";
//	private static final String SYNC_URL = "http://192.168.1.220/api/";

	private static final String SYNC_URI = "/sync";

	//wake up for no reason every ten minutes, just to check
	static final int SLEEP_TIME_SECONDS =  60 * 10;

	public static final String JSON_DATA_KEY = "data", TS_DATA_KEY = "ts",
			FILE_PATH_KEY = "path", FILE_MIME_KEY = "mime", ID_KEY = "id";

	public static final IntentFilter WifiIntentFilter = new IntentFilter();
	static {
		WifiIntentFilter
				.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		WifiIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
	}

	public final BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
			    NetworkInfo networkInfo =
			        intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			    if(networkInfo.isConnected()) {
			        syncThread.poke();
			    }
			}
		}

	};

	private final TypedString resouceName = new TypedString(getClass()
			.getSimpleName());
	private final Context mContext;
	private final String mEmail;
	public final String mTableName;
	public DBSyncHelper(Context context, String tableName) {
		super(context,DB_NAME, null, DB_VERSION);

		this.mContext = context;
		this.mTableName = tableName;
		
		String accountName = null;
		for (Account account : AccountManager.get(context).getAccountsByType(
				GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)) {
			accountName = account.name;
		}
		if (accountName == null) {
			throw new RuntimeException("Could not find any google accounts");
		}
		System.out.println("Using account " + accountName);
		mEmail = accountName;
		
		db = getWritableDatabase();
		syncThread.start();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String createTable = "CREATE TABLE " + mTableName + " (" 
				+ ID_KEY+ " INTEGER PRIMARY KEY, " 
				+ JSON_DATA_KEY + " STRING NOT NULL, " 
				+ FILE_PATH_KEY + " STRING, "
				+ FILE_MIME_KEY + " STRING, " 
				+ TS_DATA_KEY + " INTEGER NOT NULL"
			+ ")";

		String createIndices = "CREATE INDEX idx1 ON " + mTableName + "("+ TS_DATA_KEY + " ASC)";
		db.execSQL(createTable);
		db.execSQL(createIndices);
		
	}
	
	public SQLiteDatabase db = null;
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}
	


	private final DBSyncThread syncThread = new DBSyncThread(this);
	
	public void queueData(String jsonData, String filePath, String mime) {

		ContentValues cv = new ContentValues();
		cv.put(JSON_DATA_KEY, jsonData);
		cv.put(FILE_PATH_KEY, filePath);
		cv.put(FILE_MIME_KEY, mime);
		cv.put(TS_DATA_KEY, System.currentTimeMillis());
		long id = db.insert(mTableName, null, cv);
		if (id < 0) {
			throw new RuntimeException("Could not insert sync data");
		}
		syncThread.poke();

	}
	public boolean checkWifi(){
		ConnectivityManager cm =
		        (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		 
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
		if(isConnected){
			return activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
		}
		return false;
		
	}
	
	@Override
	public void close(){
		syncThread.cancel();
		super.close();
	}

	
	private final RestAdapter syncAdapter = new RestAdapter.Builder()
			.setEndpoint(SYNC_URL).build();

	private final SyncService syncService = syncAdapter
			.create(SyncService.class);


	private interface SyncService {
		@Multipart
		@POST(SYNC_URI)
		Response postSyncData(@Part("resource_name") TypedString resourceName,
				@Part("account_name") TypedString accountName,
				@Part("json_data") TypedString jsonData,
				@Part("file") TypedFile file);
	}

	/**o
	 * 
	 * @param c
	 *            Cursor moved to result which is to be synced
	 * @return true if row should be deleted
	 */
	public boolean syncRow(Cursor c) {
		TypedString jsonData = new TypedString(c.getString(c
				.getColumnIndexOrThrow(JSON_DATA_KEY)));
		String mime = c.getString(c.getColumnIndexOrThrow(FILE_MIME_KEY));
		File file = new File(
				c.getString(c.getColumnIndexOrThrow(FILE_PATH_KEY)));

		if (!file.exists()) {
			System.err.println(file.getAbsolutePath()
					+ " does not exist... skipping sync");
			return true;
		}

		Response res;
		try{
			res = syncService.postSyncData(resouceName, new TypedString(mEmail), jsonData,new TypedFile(mime, file));
		}catch(RetrofitError e){
			if(e.getResponse() != null){
				System.err.println(e.getResponse().getStatus()+" : "+e.getResponse().getReason());
			}
			e.printStackTrace();
			return false;
		}
		int rowId = c.getInt(c.getColumnIndexOrThrow(ID_KEY));

		if (res.getStatus() == 200) {
			System.out.println(String.format("syncRow: %d synced successfully",
					rowId));
			return true;
		}

		System.err.println(String.format(
				"syncRow: %d got HTTP response code %d : %s", rowId,
				res.getStatus(), res.getReason()));
		return false;
	}

}
