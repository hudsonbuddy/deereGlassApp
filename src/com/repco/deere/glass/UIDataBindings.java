package com.repco.deere.glass;

import com.repco.deere.glass.base.AlertDatum;
import com.repco.deere.glass.base.GridAdapter;

import android.graphics.Color;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.GridView;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

public class UIDataBindings {

	private static void setText(View v, int id, String val) {
		((TextView) v.findViewById(id)).setText(val);
	}
	private static void setHTML(RemoteViews v, int id, Spanned val) {
		v.setTextViewText(id, val);
	}

	private static void buildSingulation(View v, AlertDatum d) {
		setText(v, R.id.percentage_text, d.currentString +"%");
		setText(v, R.id.percentage_text2, d.aveString + "%");
		setText(v, R.id.percentage_text3, d.percentChangeString + "%");
	}

	private static void buildRideQual(View v, AlertDatum d) {
		setText(v, R.id.ridequal_now, d.currentString +"%");
		setText(v, R.id.ridequal_last, d.percentChangeString +"%");
		
		int len = d.rowData.length;
		
		for(int rowVal : d.rowData){
			 
		}
	}

	private static void buildDFMargin1(View v, AlertDatum d) {

		if (d.current > d.ave) {

			setText(v, R.id.dm_margin_text2, "All set");

		} else {

			setText(v, R.id.dm_margin_text2, "Too low");

		}
	}

	private static void buildCOV(View v, AlertDatum d) {
		setText(v, R.id.cov_row1, d.minString);
		setText(v, R.id.cov_row2, d.currentString);
		setText(v, R.id.cov_row3, d.maxString);
	}

	private static void buildActPop(View v, AlertDatum d) {
		setText(v, R.id.planted_text, d.current1000 +"k"); 
		setText(v, R.id.acres_text, d.ave1000 + "k"); 
	}

	public static void buildAlertView(View view, AlertDatum alert) {
		switch (alert.type) {
		case DF_MARGIN:
			buildDFMargin1(view, alert);
			break;
		case RIDEQUAL:
			buildRideQual(view, alert);
			break;
		case COV:
			buildCOV(view, alert);
			break;
		case ACT_POP:
			buildActPop(view, alert);
			break;
		case SINGULATION:
			buildSingulation(view, alert);
			break;
		default:
			throw new RuntimeException("Can't handle alert type " + alert.type);
		}
		((TextView)view.findViewById(R.id.timestamp)).setText(alert.getRelativeDateString());
	}

	public static void buildAlertColorView(View view, AlertDatum alert) {
		
		GridView grid = (GridView)view.findViewById(R.id.gridview);
		GridAdapter adapter = (GridAdapter)grid.getAdapter();
		adapter.putRowData(alert.rowData);
		grid.setNumColumns(alert.rowData.length);
		grid.invalidateViews();
		
		int title;
		switch (alert.type) {
		case DF_MARGIN:
			title = R.string.df_margin1;
			break;
		case RIDEQUAL:
			title = R.string.ride_qual;
			break;
		case COV:
			title = R.string.cov;
			break;
		case ACT_POP:
			title = R.string.act_pop;
			break;
		case SINGULATION:
			title = R.string.singulation;
			break;
		default:
			throw new RuntimeException("Can't handle alert type " + alert.type);
		}
		int color;
		switch(alert.color){
		case 0x01:
			color = Color.rgb(0xdd, 0xbb, 0x11);
			break;
		case 0x02:
			color = Color.rgb(0xcc, 0x33, 0x33);
			break;
		default:
			color = Color.rgb(0x99, 0xcc, 0x33);
			break;
		}
		((TextView)view.findViewById(R.id.footer_text)).setText(title);
		((RelativeLayout)view.findViewById(R.id.color_background)).setBackgroundColor(color);
		((TextView)view.findViewById(R.id.timestamp)).setText(alert.getRelativeDateString());
	}
	public static void buildConnectingLiveCardView(RemoteViews view){
		setHTML(view, R.id.body_text, Html.fromHtml("Connecting to seedstar..."));
	}
	public static void buildLiveCardView(RemoteViews view,int alertCount){
		String alertCountString = "no";
		if(alertCount > 0){
			alertCountString = Integer.toString(alertCount);
		}
		
		String s = String.format(
				"You have <font color='#99cc33'>%s</font> alerts <font color='#ddbb11'>waiting review</font>",
				alertCountString);
//		String s = "You have <font color='#cc3333'>8</font> alerts <font color='#ddbb11'>waiting review</font>";
//TODO just display how many alerts you have, and change color to green if there are none.
		setHTML(view, R.id.body_text, Html.fromHtml(s));
	}
}
