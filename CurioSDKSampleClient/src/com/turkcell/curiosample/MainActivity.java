package com.turkcell.curiosample;

import android.app.ActionBar;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.turkcell.curio.CurioClient;

public class MainActivity extends FragmentActivity implements ActionBar.OnNavigationListener {

	private static final String TAG = "MainActivity";

	/**
	 * The serialization (saved instance state) Bundle key representing the current dropdown position.
	 */
	private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//If your application receives push notification. Optional
		CurioClient.getInstance(this).getPushData(getIntent());
		
		//Custom id. Optional.
		CurioClient.getInstance(this).setCustomId("sampleCustomId");
		
		//Start session
		CurioClient.getInstance(this).startSession();

		setContentView(R.layout.activity_main);

		// Set up the action bar to show a dropdown list.
		final ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		// Set up the dropdown list navigation in the action bar.
		actionBar.setListNavigationCallbacks(
		// Specify a SpinnerAdapter to populate the dropdown list.
				new ArrayAdapter<String>(actionBar.getThemedContext(), android.R.layout.simple_list_item_1, android.R.id.text1, new String[] { getString(R.string.title_section1),
						getString(R.string.title_section2), getString(R.string.title_section3), }), this);
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		// Restore the previously serialized current dropdown position.
		if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
			getActionBar().setSelectedNavigationItem(savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		// Serialize the current dropdown position.
		outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, getActionBar().getSelectedNavigationIndex());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onNavigationItemSelected(int position, long id) {
		// When the given dropdown item is selected, show its contents in the
		// container view.
		Fragment fragment = new DummySectionFragment();
		Bundle args = new Bundle();
		args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, position + 1);
		fragment.setArguments(args);
		getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
		return true;
	}

	/**
	 * A dummy fragment representing a section of the app, but that simply displays dummy text.
	 */
	public static class DummySectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";
		private int sectionId;

		public DummySectionFragment() {
		}

		@Override
		public void onStart() {
			super.onStart();
			Log.i(TAG, "onStart of fragment");
			CurioClient.getInstance(getActivity()).startScreen(this.getClass().toString() + sectionId, "Section ğüşıçö " + sectionId + " screen", "Sec" + sectionId);
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main_dummy, container, false);
			TextView dummyTextView = (TextView) rootView.findViewById(R.id.section_label);
			sectionId = getArguments().getInt(ARG_SECTION_NUMBER);
			dummyTextView.setText(sectionId + "");

			switch (sectionId) {
			case 1:
				rootView.setBackgroundColor(Color.CYAN);
				break;
			case 2:
				rootView.setBackgroundColor(Color.YELLOW);
				break;
			case 3:
				rootView.setBackgroundColor(Color.MAGENTA);
				break;

			default:
				break;
			}
			return rootView;
		}

		@Override
		public void onStop() {
			super.onStop();
			Log.i(TAG, "onStop of fragment");
			CurioClient.getInstance(getActivity()).endScreen(this.getClass().toString() + sectionId);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			Log.i(TAG, "onDestroy of fragment");
		}
	}

	public void startNewActivity(View v) {
		CurioClient.getInstance(this).sendEvent("buttonClick", "start button");
		Intent intent = new Intent(this, BlankActivity.class);
		startActivity(intent);
	}

	@Override
	protected void onStart() {
		super.onStart();
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
		if (isFinishing()) {
			CurioClient.getInstance(this).endSession();
		}
		Log.i(TAG, "onStop called. isFinishing: " + isFinishing());
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy called. isFinishing: " + isFinishing());
	}
}
