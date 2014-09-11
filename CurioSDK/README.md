#Curio Android SDK 1.0

[Curio](https://gui-curio.turkcell.com.tr) is Turkcell's mobile analytics system, and this is Curio's Android Client SDK library. Applications developed for ***Android 2.2 Froyo (API Level 8) and higher*** can easily use Curio mobile analytics with this library.

#Quick Startup Guide

##Configuration:

All configuraiton of Curio is made through XML configuration file. For this, create an XML file named ***curio.xml*** inside the ***"res/values"*** folder of your application project. Sample content of the curio.xml file is as below:

    <?xml version="1.0" encoding="utf-8"?>
	<resources>
	    <string name="server_url">https://sampleurl.com/sample/</string>
	    <string name="api_key">your api key</string>
	    <string name="tracking_code">your tracking code</string>
	    <integer name="session_timeout">15</integer>
	    <bool name="periodic_dispatch_enabled">false</bool>
	    <integer name="dispatch_period">5</integer>
	    <integer name="max_cached_activity_count">1000</integer>
	    <bool name="logging_enabled">false</bool>
	</resources>

####Configuration Parameters:

**server_url:** [Required] Curio server URL, can be obtained from Turkcell. 

**api_key:** [Required] Application specific API key, can be obtained from Turkcell.

**tracking_code:** [Required] Application specific tracking code, can be obtained from Turkcell.

**session_timeout:** [Optional] Session timeout in minutes. Default is 30 minutes but it's highly recommended to change this value acording to the nature of your application. Specifiying a correct session timeout value for your application will increase the accuracy of the analytics data.

**periodic\_dispatch\_enabled:** [Optional] Periodic dispatch is enabled if true. Default is false.

**dispatch_period:** [Optional] If periodic dispatch is enabled, this parameter configures dispatching period in minutes. Deafult is 5 minutes. **Note:** This parameter cannot be greater than session timeout value.

**max\_cached\_activity\_count:** [Optional] Max. number of user activity that Curio library will remember when device is not connected to the Internet. Default is 1000. Max. value can be 4000.

**logging_enabled:** [Optional] All of the Curio logs will be disabled if this is false. Default is true.

##Usage:

###Instance Creation and Starting Session:
Instance creation and session start should be in onCreate() method of application's main (or enterance) activity class.

	protected void onCreate(Bundle savedInstanceState) {
		...
		CurioClient.createInstance(this);
		CurioClient.getInstance().startSession();
		...
	}

###Starting Screen:

Should be called once per activity class or fragment.

	protected void onStart() {
		CurioClient.getInstance().startScreen(SampleActivity.this, "Sample Activity", "sample");
		...
	}

###Ending Screen:
Should be called once per activity class or fragment.

	protected void onStop() {
		CurioClient.getInstance().endScreen(SampleActivity.this);
		...
	}

###Sending Event:
	...
	CurioClient.getInstance().sendEvent("sample event key", "sample event value");
	...

###Ending Session:
Session ending should be in onStop() method of application's main (or exit) activity class, and also it should be checked if application is actually finishing or just going to another activity with isFinishing() method. This check should be made on main (or exit) activity class.

	protected void onStop() {
		...
		if(isFinishing()){
			CurioClient.getInstance().endSession();
		}
		...
	}

Actually that's all you need to do in your application for a quick and simple integration. For more detail, please keep reading next sections.

#API and Usage Details
##Initialization
First thing to use Curio is creating the singleton instance by calling:
 
	protected void onCreate(Bundle savedInstanceState) {
		...
		CurioClient.createInstance(this);
		...
	}

once at the begining of application. Calls to other methods such as getInstance() etc. before creating the instance will throw an illegalStateException.
###Starting Session
By starting session, you tell Curio server that application is launched, so before doing anything else you should start a session at the very beginning of the application by calling:

	protected void onCreate(Bundle savedInstanceState) {
		...
		CurioClient.getInstance().startSession();
		...
	}

**once** in onCreate method of the main (or enterance) activity. A session will be closed by the server if no request sent for a time period defined by **session_timeout** parameter in curio.xml. After the session has timed out, your requests will get HTTP 401 unauthorized response from server and SDK will start a new session automatically for you and then send the request with new session code.

**Important:** If your application supports multiple orientations, Android OS may call `onCreate()` of your activities after a device orientation change depending on your application's AndroidManifest.xml configuration. Developers are responsible for handling session start calls on orientation changes. If `onCreate()` method is called on each device orientation change, application should detect it and should not call `startSession()` more than once per application launch. If this should not handled properly, application will produce wrong analytics data.

**Methods:**

`public void startSession()`

`public void startSession(final boolean generate)`

**Parameters:**

***generate:*** If this parameter is true, method forces to generate a new session code. 

###Starting Screen
By starting session, you tell Curio server that a new Activity (application screen) is displayed by user. So you should start a new screen in `onStart()` method of each activity that you'd like to track by calling:
	
	protected void onStart() {
		CurioClient.getInstance().startScreen(SampleActivity.this, "Sample Activity", "sample");
		...
	}
   
**Methods:**
 
`public void startScreen(Context context, String title, String path)`

`public void startScreen(final String className, final String title, final String path)`

**Parameters:**

***context or className:*** This parameter is used as a key for the screen and thus it should be unique for every screen (means an activity, service or fragment). If using in a class that's not unique for each screen (such as fragments), String className parameter can be used instead of Activity instance. But do not forget each className parameter should be unique for each screen.

	protected void onStart() {
		CurioClient.getInstance().startScreen("sample_Fragment_1", "Sample Activity", "sample");
		...
	}

***title:*** Screen title, unique for the screen.

***path*** Screen path, unique for the screen.
###Ending Screen
By ending session, you tell Curio server that the current Activity is finished by user. **So you should call `endScreen()` method in `onStop()` method of each activity that you start tracking by calling `startScreen()`.** You can end a screen by calling:
	
	protected void onStart() {
		CurioClient.getInstance().endScreen(SampleActivity.this);
		...
	}

**Please note that Android API Level 11 (Honeycomb) or higher OS's always call `onStop()` method of Activities or Fragments when leaving that Activity. So it's safe to use `onStop()` method for screen ending on API Level 11 or higher. But prior to Honeycomb, Activities can be killed before OS calls `onStop()`, so according to the nature of your application you can use `onPause()` method instead of `onStop()` if your application runs on an Android version prior to Honeycomb.**
   
**Methods:**
 
`public void endScreen(Context context)`

`public void endScreen(final String className)`

**Parameters:**

***context or className:*** This parameter is used as a key for the screen and thus it should be unique for every screen (means an activity, service or fragment). If using in a class that's not unique for each screen (such as fragments), String className parameter can be used instead of Activity instance. But do not forget each className parameter should be unique for each screen.

	protected void onStart() {
		CurioClient.getInstance().startScreen("sample_Fragment_1");
		...
	}

###Sending Event
You can track certain user actions like button clicks, user choices, etc. by sending custom events to Curio. You can send events by calling:

	...
	CurioClient.getInstance().sendEvent("sample event key", "sample event value");
	...
 
**Methods:**
 
`public void sendEvent(String eventKey, String eventValue)`


**Parameters:**

***eventKey:*** This parameter is used as a key for the custom event or event group. Sample: "button_click"

***eventValue:*** This parameter is used as a value for the custom event. Sample: "login_button"
###Ending Session
By ending session, you tell Curio server that user has quit from the application.
Session ending should be in onStop() method of application's main (or exit) activity class, and also it should be checked if application is actually finishing or just going to another activity with isFinishing() method.

	protected void onStop() {
		...
		if(isFinishing()){
			CurioClient.getInstance().endSession();
		}
		...
	}


**Methods:**

`public void endSession()`

##Periodic Dispatch and Offline Cache
To save bandwith and batterly life of device, Curio client SDK can dispatch requests periodically instead of sending every request real-time. When periodic dispatch is enabled from curio.xml configuration file, only session start and session end requests are delivered real-time, other requests are delivered automatically each time when dispatch period is over. You can configure dispatch period from configuration XML.

Also Curio client SDK has an internal cache for storing analitics data when device is offline. This offline data cache is enabled automatically when device network connectivity is not available. In same way, stored offline analytics data is sent to server automatically when device goes online again. You can configure the capacity of this offline cache from configuration XML.