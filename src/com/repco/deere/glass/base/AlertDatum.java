package com.repco.deere.glass.base;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Date;

import android.content.Context;
import android.text.format.DateUtils;

import com.repco.deere.glass.AlertService.AlertTypes;

public class AlertDatum{
	public final float max;
	public final float min;
	public final float current;
	public final float ave;

	public final float percentChange;
	public final String percentChangeString;


	public final String current1000;
	public final String ave1000;

	public final String currentString;
	public final String currentIntString;
	public final String aveIntString;
	public final String maxIntString;
	public final String minIntString;
	public final String aveString;
	public final String maxString;
	public final String minString;
	public final byte color;
	public final AlertTypes type;
	public final Date ts;
	public final long ts_millis;
	
	public final int[] rowData;
	public final float[] locationData;
	
	public AlertDatum(AlertTypes type,byte[] data){
		this.type = type;
		this.ts = new Date();
		this.ts_millis = this.ts.getTime();

		this.current = ByteBuffer.wrap(Arrays.copyOfRange(data, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getFloat();
		this.current1000 = Integer.toString(Math.round(current/1000));
		this.currentString = String.format("%.2f", current);
		this.currentIntString = Integer.toString(Math.round(current));
		
		this.ave = ByteBuffer.wrap(Arrays.copyOfRange(data, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getFloat();
		this.ave1000 = Integer.toString(Math.round(ave/1000));
		this.aveString = String.format("%.2f", ave);
		this.aveIntString = Integer.toString(Math.round(ave));

		this.max = ByteBuffer.wrap(data, 8, 12).order(ByteOrder.LITTLE_ENDIAN).getFloat();
		this.maxString = String.format("%.2f", max);
		this.maxIntString = Integer.toString(Math.round(max));

		this.min = ByteBuffer.wrap(data, 12, 16).order(ByteOrder.LITTLE_ENDIAN).getFloat();
		this.minString = String.format("%.2f", min);
		this.minIntString = Integer.toString(Math.round(min));

		this.percentChange = ByteBuffer.wrap(data, 16, 20).order(ByteOrder.LITTLE_ENDIAN).getFloat();
		this.percentChangeString = Integer.toString(Math.round(percentChange));
		
		this.color = data[20];
		
		//begin location data
		int locStart = 21;
		int locLength = 3*4;
		ByteBuffer locationBuffer = ByteBuffer.wrap(data, locStart, locLength).order(ByteOrder.LITTLE_ENDIAN);
		
		locationData = new float[]{
				locationBuffer.getFloat(locStart), //lat
				locationBuffer.getFloat(locStart+4), //long
				locationBuffer.getFloat(locStart+8)}; //heading
		
		//begin row data
		int rowStart = locStart+locLength;
		int rowByteCount = data.length - rowStart;
		System.out.println(rowStart+" "+rowByteCount);

		rowData = new int[rowByteCount];
		for(int i = 0; i < rowByteCount; i++ ){
			rowData[i] = new Byte(data[rowStart+i]).intValue();
		}

	}
	


	public int getResourceId(Context ctx){
		return ctx.getResources().getIdentifier(type.name().toLowerCase(), "layout", ctx.getPackageName());
	}
	
	public CharSequence getRelativeDateString(){
		return DateUtils.getRelativeTimeSpanString(ts.getTime(), new Date().getTime(), DateUtils.SECOND_IN_MILLIS, 0);
	}
	@Override
	public String toString() {
		return String.format("\n\n--AlertDatum--\n\t"
				+ "Type: %s \n\t"
				+ "Ts: %s \n\t"
				+ "Current: %s \n\t"
				+ "Ave: %s \n\t"
				+ "Max: %s \n\t"
				+ "Min: %s \n\t"
				+ "PercentChange: %s \n\t"
				+ "Color: %x \n\t"
				+ "LocationData: %s \n\t"
				+ "RowData: %s \n\t",
				type,ts,current,ave,max,min,percentChange,color,Arrays.toString(locationData), Arrays.toString(rowData));
	}

}
