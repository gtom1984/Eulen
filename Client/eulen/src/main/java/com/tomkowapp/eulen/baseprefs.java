package com.tomkowapp.eulen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;

// base class with reusable methods and variables for other classes
// mostly used for logon components

public class baseprefs extends AppCompatActivity {
    SharedPreferences prefs;
    String userID = null;
    String password = null;
    String account = null;
    String databaseKey = null;

    protected void lockScreen(Activity activity) {
        ((app) this.getApplication()).closeDatabase();
        Intent intent = new Intent(activity, login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // control the availability of the sync
    protected Boolean syncSafe() {
        return ((app) this.getApplication()).getSafeToSync();
    }

    protected void setSyncSafe(Boolean syncSafe) {
        ((app) this.getApplication()).setSafeToSync(syncSafe);
    }

    // check if database is closed
    protected void checkLock(Activity activity) {
        if(((app) this.getApplication()).getLockstate() ||
                ((app) this.getApplication()).getDatabase() == null ||
                !((app) this.getApplication()).getDatabase().isOpen()) {
            lockScreen(activity);
        }
    }

    protected void toStart() {
        Intent intent = new Intent(this, start.class);
        intent.setAction("");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // set credentials
    protected void setCreds() {
        ((app) this.getApplication()).setCreds(userID, password);
    }

    protected void openDatabase(String databaseKey) {
        ((app) this.getApplication()).openDatabase(databaseKey);
    }

    // get creds stored in application
    protected boolean copyCreds() {
        prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);
        account = prefs.getString(CONST.PREFS_ACCOUNT, null);
        userID = ((app) this.getApplication()).getUserID();
        password = ((app) this.getApplication()).getPassword();

        return account != null &&
                userID != null &&
                password != null &&
                ((app) this.getApplication()).getDatabase() != null;
    }

    protected boolean getCreds() {
        if(copyCreds()) {
            return true;
        } else {
            toStart();
            return false;
        }
    }

    protected void gotoMain(Activity activity) {
        //go to main activity
        Intent intent;
        intent = new Intent(activity, main.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    public void diagError(String msg) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(getString(R.string.alert_title_error));
        alert.setMessage(msg);
        alert.setPositiveButton(getString(R.string.ok), null);
        alert.show();
    }
}
