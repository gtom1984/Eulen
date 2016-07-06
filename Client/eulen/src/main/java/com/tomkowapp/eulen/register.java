package com.tomkowapp.eulen;

import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import org.json.JSONObject;

// actions for registering a new user on device

public class register extends nfctagbase implements AsyncResponse{
    final int REQUEST_CODE = 1; //request code for pick google account intent
    private String pin;
    private String regid;
    private int connectAttempts = 0;
    private static EditText editTextAccount;
    private static EditText editTextPassphrase;
    private static EditText editTextPin;
    private static Button buttonRegister;
    private EulenClient eulenClient;

    private EulenUtils eulenUtils = new EulenUtils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            String data = extras.getString(CONST.PREFS_REGID);
            if (data != null) {
                regid = data;
            } else {
                finish();
            }
        } else {
            finish();
        }

        startNFC(this); // start NFC

        setContentView(R.layout.activity_register);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbarRegister);
        setSupportActionBar(toolbar);
        if(toolbar != null) {
            toolbar.setTitle(getString(R.string.title_activity_register));
        }

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.containerRegister, new PlaceholderFragment())
                    .commit();
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.fragment_register, container, false);

            editTextAccount = (EditText) rootView.findViewById(R.id.editTextAccount);
            editTextPassphrase = (EditText) rootView.findViewById(R.id.editTextPassphrase);
            editTextPin = (EditText) rootView.findViewById(R.id.editTextPin);
            buttonRegister = (Button) rootView.findViewById(R.id.buttonRegister);

            return rootView;
        }
    }

    // handle account selcction intent return
    protected void onActivityResult(final int requestCode, final int resultCode,
                                    final Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            editTextAccount.setText(accountName);
        } else if (requestCode == 2) {
            if(connectAttempts < 1) {
                registerAccount(account, passphrase, pin, regid); //try again if first failed because of permission
            } else {  //if second failure, alert user and terminate application
                diagError(getString(R.string.alert_registration));
            }
            connectAttempts++;
        }
    }


    // handle account selection
    public void selectAccount(View view) { //handle click for Google Account edit text
        Intent googlePicker = AccountPicker.newChooseAccountIntent(null, null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE},
                true, null, null, null, null);

        startActivityForResult(googlePicker, REQUEST_CODE);
    }

    // register button action
    public void registerButton(View view) { //handle register button
        if(validate()) {
            registerAccount(account, passphrase, pin, regid);
        }
    }

    // post NFC override for post NFC action
    @Override
    protected void postNFC() {
        registerAccount(account, passphrase, pin, regid);
    }

    // validate registration form
    @Override
    protected boolean validate() {
        String hash;
        Boolean connection;

        hash = eulenUtils.sha(editTextPassphrase.getText().toString());
        connection = eulenUtils.networkConnection(this);

        if(connection) {
            if (TextUtils.isEmpty(
                    editTextAccount.getText().toString()) ||
                    editTextPassphrase.getText().length() < 8 ||
                    editTextPin.getText().length() < 4 ||
                    hash == null) {
                diagError(getString(R.string.alert_validation));
                return false;
            } else {
                account = editTextAccount.getText().toString();
                passphrase = hash;
                pin = editTextPin.getText().toString();
                return true;
            }
        }
        return false;
    }

    // register new account on server
    private void registerAccount(
            final String account,
            final String passphrase,
            final String pin,
            final String regid) {
        lockGUI();
        databaseKey = eulenUtils.sha(passphrase + pin);
        eulenClient = new EulenClient(this, this, this, account);
        eulenClient.register(regid, 1);
    }

    public void serverError(int returnCode, String code) {
        if(code.equals(CONST.EXISTS) & returnCode == 1) { // if account exists
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.alert_reregister))
                    .setPositiveButton(getString(R.string.yes), regDialogClickListener)
                    .setNegativeButton(getString(R.string.no), regDialogClickListener)
                    .show();
        } else { // all other errors, unlock GUI and inform user of generic error
            unlockGUI();
            Toast toast = Toast.makeText(getApplicationContext(),
                    getString(R.string.error_register),
                    Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        }
    }

    // choice to re-register or try again
    DialogInterface.OnClickListener regDialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    eulenClient.delete(2);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    unlockGUI();
                    break;
            }
        }
    };

    public void postServer(int returnCode, JSONObject result) {

        if(returnCode == 1) {  // register new account
                try {
                    userID = result.getString(CONST.DATA);
                    password = result.getString(CONST.PASSWORD);
                } catch (Exception e) {
                    // do nothing
                }

                if(userID != null & password != null) {
                    EulenDatabase eulenDatabase = new EulenDatabase(this, this, this);
                    eulenDatabase.createDB(userID, password, databaseKey, 1);
                }
        }

        if(returnCode == 2) { // register account after deleting old account
            registerAccount(account, passphrase, pin, regid);
        }
    }

    public void postDatabase(int returnCode, Cursor cursor) {
        if(returnCode == 1) { // log into app and record preferences
            SharedPreferences prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);

            //write preferences
            prefs.edit().putString(CONST.PREFS_ACCOUNT, account).apply();
            prefs.edit().putBoolean(CONST.PREFS_REGISTERED, true).apply();
            prefs.edit().putInt(CONST.PREFS_DB_VERSION, 2).apply();

            setCreds();
            gotoMain(this);
        }
    }

    // lock or unlock gui elements
    private void lockGUI() {
        editTextAccount.setEnabled(false);
        editTextPassphrase.setEnabled(false);
        editTextPin.setEnabled(false);
        buttonRegister.setEnabled(false);
    }

    private void unlockGUI() {
        editTextAccount.setEnabled(true);
        editTextPassphrase.setEnabled(true);
        editTextPin.setEnabled(true);
        buttonRegister.setEnabled(true);
    }
}
