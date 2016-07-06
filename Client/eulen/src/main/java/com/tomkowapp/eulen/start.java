package com.tomkowapp.eulen;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import org.json.JSONObject;

import java.io.IOException;

// app start splash screen and startup procedure

public class start extends baseprefs implements AsyncResponse {
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    GoogleCloudMessaging gcm;
    Boolean registered;
    int appVersionStored;
    int appVersion;
    int retriesGCM = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Bundle extras = getIntent().getExtras(); //login gets values from keys and stores

        prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);

        if (extras != null) {
            String data = extras.getString(CONST.DELETE);
            if (data != null && data.equals(CONST.DELETE)) { // handle DB deletion for fresh start if user account deleted from app
                EulenDatabase eulenDatabase = new EulenDatabase(this, this, this);
                eulenDatabase.deleteDB(1);
                setSyncSafe(true);
                startup();
            } else {
                startup(); // start up app
            }
        } else {
            startup();
        }

    }

    // app start procedure
    private void startup() {
        String regid;

        try { // get app version
            PackageInfo getVersion = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = getVersion.versionCode;

        } catch (PackageManager.NameNotFoundException e) {
            appVersion = 0;
        }

        registered = prefs.getBoolean(CONST.PREFS_REGISTERED, false);
        regid = prefs.getString(CONST.PREFS_REGID, null);
        appVersionStored = prefs.getInt(CONST.PREFS_VERSION, 0);

        // setup db migration if version less than 18 or not null

        if(checkPlayServices()) { // check if GCM is good
            gcm = GoogleCloudMessaging.getInstance(this);
            if(regid == null || appVersion != appVersionStored) {
                getRegid(); // get reg ID for GCM
            } else {
                postRegid(regid);
            }
        }

        ImageView image=(ImageView)findViewById(R.id.imageProgress);
        RotateAnimation anim = new RotateAnimation(
                0f,
                360f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(Animation.INFINITE);
        anim.setDuration(1500);
        if(image != null) {
            image.startAnimation(anim);
        }
    }

    // placeholders for interface
    public void postServer(int returnCode, JSONObject result) {

    }
    public void serverError(int returnCode, String code) {

    }

    public void postDatabase(int returnCode, Cursor cursor) {
        if(returnCode == 1) { // after database deleted
            startup();
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        checkPlayServices();
    }

    // check if Google Play Services is good
    protected boolean checkPlayServices() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            if(googleAPI.isUserResolvableError(result)) {
                    googleAPI.getErrorDialog(this, result,
                            PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                failedGCM();
            }
            return false;
        }
        return true;
    }

    protected void getRegid() {
        new AsyncTask<Void, String, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String response;

                    try {
                        InstanceID instanceID = InstanceID.getInstance(getApplicationContext());
                        response = instanceID.getToken(getString(R.string.gcm_defaultSenderId),
                                GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

                        prefs.edit().putString(CONST.PREFS_REGID, response).apply(); //store regid
                        prefs.edit().putBoolean(CONST.PREFS_REG_UPDATED, true).apply(); //store flag to transmit new reg ID
                    } catch (IOException ex) {
                        response = null;
                    }

                return response;
            }

            @Override
            protected void onPostExecute(String response) {

                if(response == null) {
                    if(retriesGCM < 5) { //GCM sometimes fails upon first try, 5 tries over 9 sec might be needed
                        retriesGCM++;
                        try {
                            wait(9000);
                        } catch (Exception e) {
                            // do nothing
                        }
                        try{
                        getRegid();
                        } catch (Exception ex) {
                            //do nothing
                        }
                    } else {
                        try{
                        failedGCM();
                        } catch (Exception ex) {
                            //do nothing
                        }
                    }
                } else {
                    try{
                    postRegid(response);
                    } catch (Exception ex) {
                        //do nothing
                    }
                }
            }
        }.execute();
    }

    private void postRegid(final String regid) {
        Intent intent;

        if (registered) { //if first run, execute the registration activity
            // go to login
            intent = new Intent(this, login.class);
        } else {
            // go to registration
            intent = new Intent(this, register.class);
        }

        prefs.edit().putInt(CONST.PREFS_VERSION, appVersion).apply(); //update app version
        intent.putExtra(CONST.PREFS_REGID, regid);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    // if GCM is not OK
    private void failedGCM(){
        Toast toast = Toast.makeText(getApplicationContext(),
                getString(R.string.alert_gcm_fail),
                Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
        finish();
    }
}
