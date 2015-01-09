package com.turkcell.curiosample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

import com.turkcell.curio.CurioClient;

public class BlankActivity extends Activity {

	private static final String TAG = "BlankActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate called. isFinishing: " + isFinishing());
		setContentView(R.layout.activity_blank);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.blank, menu);
		return true;
	}

	
	@Override
	protected void onStart() {
		super.onStart();
		CurioClient.getInstance(this).startScreen(this, "Blank Activity", "blank");
		Log.i(TAG, "onStart called. isFinishing: " + isFinishing());
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "onResume called. isFinishing: " + isFinishing());
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		Log.i(TAG, "onPause called. isFinishing: " + isFinishing());
	}

	@Override
	protected void onStop() {
		super.onStop();
		CurioClient.getInstance(this).endScreen(this);
		Log.i(TAG, "onStop called. isFinishing: " + isFinishing());
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy called. isFinishing: " + isFinishing());
	}
}
