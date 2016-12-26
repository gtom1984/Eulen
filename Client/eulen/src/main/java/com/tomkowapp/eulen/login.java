package com.tomkowapp.eulen;

import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.widget.Toolbar;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

// handles application login

public class login extends nfctagbase implements AsyncResponse {

    private String[] values = null;
    private static String NFCResult = null;
    private static String savedPassphrase = null;
    private EulenDatabase eulenDatabase;

    // constructor for NFC
    public login() {
        tagDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
    }

    EulenUtils eulenUtils = new EulenUtils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras(); // login gets values from keys and stores, this is used if the login is trigger when receiving beamed keys
        if (extras != null) {
            String data[] = extras.getStringArray(CONST.VALUES);
            if (data != null) {
                values = data;
            }
        }

        // check lock state
        ((app) this.getApplication()).setLockstate(true);
        startNFC(this);

        handleIntent(getIntent());

        prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);

        // check if passphrase is saved
        savedPassphrase = prefs.getString(CONST.PREFS_PASSPHRASE, null);

        setContentView(R.layout.activity_login);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbarLogin);
        setSupportActionBar(toolbar);
        if(toolbar != null) {
            toolbar.setTitle(getString(R.string.title_activity_login));
        }

        EditText editTextPassphrase = (EditText) findViewById(R.id.editTextPassphraseLogin);
        EditText editTextPin = (EditText) findViewById(R.id.editTextPinLogin);
        CheckBox checkRemember = (CheckBox) findViewById(R.id.checkRemember);

        // handle views for NFC tag logins and saved passphrase
        if(NFCResult != null) {
            editTextPassphrase.setText(getString(R.string.text_passphrase));
            editTextPassphrase.setTransformationMethod(null);
            editTextPassphrase.setEnabled(false);
        } else if (savedPassphrase != null) {
            editTextPassphrase.setText(getString(R.string.text_saved));
            editTextPassphrase.setTransformationMethod(null);
            editTextPassphrase.setEnabled(false);
            checkRemember.setChecked(true);
            checkRemember.setEnabled(false);
            editTextPin.setFocusableInTouchMode(true);
            editTextPin.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            checkRemember.setChecked(false);
            checkRemember.setEnabled(true );
        }
    }

    public void clearButton(View view) {
        clearForm();
    }

    private void clearForm() {
        EditText editTextPassphrase = (EditText) findViewById(R.id.editTextPassphraseLogin);
        EditText editTextPin = (EditText) findViewById(R.id.editTextPinLogin);
        CheckBox checkRemember = (CheckBox) findViewById(R.id.checkRemember);

        savedPassphrase = null;
        NFCResult = null;
        prefs.edit().remove(CONST.PREFS_PASSPHRASE).apply();
        checkRemember.setChecked(false);
        checkRemember.setEnabled(true);
        editTextPassphrase.setText(null);
        editTextPassphrase.setEnabled(true);
        editTextPin.setText(null);
        editTextPassphrase.setTransformationMethod(new PasswordTransformationMethod());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        editTextPin.clearFocus();
        editTextPassphrase.clearFocus();
        enableGUI();
    }

    public void loginButton(View view) {
        EditText editTextPassphrase = (EditText) findViewById(R.id.editTextPassphraseLogin);
        EditText editTextPin = (EditText) findViewById(R.id.editTextPinLogin);
        Button buttonLogin = (Button) findViewById(R.id.buttonLogin);
        Button buttonClear = (Button) findViewById(R.id.buttonClear);
        CheckBox checkRemember = (CheckBox) findViewById(R.id.checkRemember);

        String passphrase;
        String pin;

        //if tag was swipped passphrase is already hashed, if hand entered then hash
        if(NFCResult == null & savedPassphrase == null) {
            passphrase = eulenUtils.sha(editTextPassphrase.getText().toString());
        } else if (NFCResult != null){
            passphrase = NFCResult;
        } else {
            passphrase = savedPassphrase;
        }

        if (editTextPassphrase.getText().length() < 8 || editTextPin.getText().length() < 4 || passphrase == null) {
            diagError(getString(R.string.alert_login));
            clearForm();
        } else {
            pin = editTextPin.getText().toString();
            databaseKey = eulenUtils.sha(passphrase + pin);

            editTextPassphrase.setEnabled(false);
            editTextPin.setEnabled(false);
            buttonLogin.setEnabled(false);
            buttonClear.setEnabled(false);

            try {
                openDatabase(databaseKey);
                eulenDatabase = new EulenDatabase(this, this, this);

                if(checkRemember.isChecked()) {
                    savedPassphrase = passphrase;
                    prefs.edit().putString(CONST.PREFS_PASSPHRASE, savedPassphrase).apply();
                } else {
                    prefs.edit().remove(CONST.PREFS_PASSPHRASE).apply();
                }
            } catch (Exception e) {
                eulenDatabase = null;
                diagError(getString(R.string.alert_login_error));
                clearForm(); // clear login if they enter incorrect creds
                prefs.edit().remove(CONST.PREFS_PASSPHRASE).apply();
            }

            // trigger database upgrade if login is correct and DB schema is out of date
            if(eulenDatabase != null) {
                if(prefs.getInt(CONST.PREFS_DB_VERSION, 1) == 1) {
                    eulenDatabase.upgradeDB_1(2);
                } else {
                    eulenDatabase.getCreds(1);
                }
            }
        }
    }

    @Override
    protected void postResume() {
        savedPassphrase = prefs.getString(CONST.PREFS_PASSPHRASE, null);

        if(NFCResult != null || savedPassphrase != null) {
            EditText editTextPin = (EditText) findViewById(R.id.editTextPinLogin);
            if(editTextPin != null) {
                editTextPin.setFocusableInTouchMode(true);
                editTextPin.requestFocus();
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }
        }
    }

    // handle NFC tag passphrase entry
    @Override
    public void handleIntent(Intent intent) {
        ((app) this.getApplication()).setLockstate(true);

            if(NFCResult == null) {
                try {
                    if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
                        if(savedPassphrase != null) {
                            diagError(getString(R.string.diaglog_nfc_saved_error));
                            return;
                        }

                        NdefMessage msgs[];

                        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
                            if (CONST.NFC_MIME_LOGIN.equals(intent.getType())) {
                                Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                                if (rawMsgs != null) {
                                    msgs = new NdefMessage[rawMsgs.length];
                                    for (int i = 0; i < rawMsgs.length; i++) {
                                        msgs[i] = (NdefMessage) rawMsgs[i];
                                        NdefRecord record = msgs[0].getRecords()[0];

                                        byte[] payload = record.getPayload();
                                        NFCResult = new String(payload);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), getString(R.string.toast_nfcfail), Toast.LENGTH_SHORT).show();
                }
            }

    }

    // GUI unlock for login screen
    private void enableGUI() {
        EditText editTextPassphrase = (EditText) findViewById(R.id.editTextPassphraseLogin);
        EditText editTextPin = (EditText) findViewById(R.id.editTextPinLogin);
        Button buttonLogin = (Button) findViewById(R.id.buttonLogin);
        Button buttonClear = (Button) findViewById(R.id.buttonClear);

        if(NFCResult == null && editTextPassphrase != null) {
            editTextPassphrase.setEnabled(true);
        }
        if(editTextPin != null & buttonLogin != null & buttonClear != null) {
            editTextPin.setEnabled(true);
            buttonLogin.setEnabled(true);
            buttonClear.setEnabled(true);
        }
    }

    public void postDatabase(int returnCode, Cursor cursor) {
        if(returnCode == 1) { // get Eulen Server creds from DB
            if(cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        String item = cursor.getString(1);
                        String value = cursor.getString(2);
                        if(item.equals(CONST.USERID)) {
                            userID = value;
                        }
                        if(item.equals(CONST.PASSWORD)) {
                            password = value;
                        }
                    }

                    cursor.close();
                } catch (Exception e) {
                    diagError(getString(R.string.alert_login_error));
                    clearForm();
                }
            }

            if (userID != null || password != null) { // direct user back to beam screen if login was triggered by receiving keys when user was NOT logged in

                ((app) this.getApplication()).setLockstate(false);

                setCreds();

                if(values == null) {
                    NFCResult = null;
                    gotoMain(this);
                    finish();
                } else {
                    Intent intent;
                    intent = new Intent(this, beam.class);
                    intent.putExtra(CONST.VALUES, values);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
            } else {
                diagError(getString(R.string.alert_login_error));
                clearForm();
            }
        }

        if(returnCode == 2) { // post DB schema update
            // update db version
            prefs.edit().putInt(CONST.PREFS_DB_VERSION, 2).apply();

            // continue post upgrade
            eulenDatabase.getCreds(1);
        }
    }

    // placeholders for callback interface
    public void postServer(int returnCode, JSONObject jsonObject) {

    }

    public void serverError(int returnCode, String code) {

    }
}
