package com.tomkowapp.eulen;

import android.app.AlertDialog;
import android.app.Fragment;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

// handle database key changes (change passphrase or pin)

public class rekey extends nfctagbase implements AsyncResponse {
    private static EditText editTextPassphrase;
    private static EditText editTextPin;
    private static Button buttonLogin;
    private static Button buttonClear;

    String pin;
    EulenDatabase eulenDatabase;
    EulenUtils eulenUtils = new EulenUtils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkLock(this);

        startNFC(this);
        handleIntent(getIntent());

        setContentView(R.layout.activity_login);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbarLogin);
        setSupportActionBar(toolbar);

        if(toolbar != null) {
            toolbar.setTitle(getString(R.string.title_activity_rekey));
        }

        if(getCreds()) {
            eulenDatabase = new EulenDatabase(this, this, this);
        }

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.containerLogin, new PlaceholderFragment())
                    .commit();
        }


        // warning to user
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(getString(R.string.warning));
        alert.setMessage(getString(R.string.warning_rekey));
        alert.setCancelable(false);
        alert.setPositiveButton(getString(R.string.ok), null);
        alert.show();
    }

    @Override //NFC switch
    public void onResume(){
        super.onResume();

        copyCreds();

        enableNfcWrite();
        postResume();
    }

    // placeholder fragment
    public static class PlaceholderFragment extends Fragment {
        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_login, container, false);

            TextView textPassphrase;
            TextView textPin;
            TextView textNFC;

            editTextPassphrase = (EditText) rootView.findViewById(R.id.editTextPassphraseLogin);
            editTextPin = (EditText) rootView.findViewById(R.id.editTextPinLogin);
            buttonLogin = (Button) rootView.findViewById(R.id.buttonLogin);
            buttonClear = (Button) rootView.findViewById(R.id.buttonClear);
            textPassphrase = (TextView) rootView.findViewById(R.id.textPassLogin);
            textPin = (TextView) rootView.findViewById(R.id.textPinLogin);
            textNFC = (TextView) rootView.findViewById(R.id.textNFCLogin);
            textNFC.setText(getString(R.string.title_NFC_rekey));
            buttonClear.setText(getString(R.string.button_cancel));
            buttonLogin.setText(getString(R.string.button_set));
            textPassphrase.setText(getString(R.string.title_passphrase_new));
            textPin.setText(getString(R.string.title_pin_new));

            return rootView;
        }
    }

    // clear form
    public void clearButton(View view) {
        gotoMain(this);
    }

    // rekey DB
    public void loginButton(View view) {
        if(validate()) {
            rekeyDatabase(passphrase, pin);
        }
    }

    // validate input
    @Override
    protected boolean validate() {
        String hash;

        hash = eulenUtils.sha(editTextPassphrase.getText().toString());

            if (editTextPassphrase.getText().length() < 8 || editTextPin.getText().length() < 4 || hash == null) {
                diagError(getString(R.string.alert_validation_rekey));
                return false;
            } else {
                passphrase = hash;
                pin = editTextPin.getText().toString();
                return true;
            }
    }

    @Override
    protected void postResume() {
        checkLock(this);
    }

    // post NFC tag write
    @Override
    protected void postNFC() {
        rekeyDatabase(passphrase, pin);
    }

    // rekey the database
    private void rekeyDatabase(final String passphrase, final String pin) {
        lockGUI();
        prefs.edit().remove(CONST.PREFS_PASSPHRASE).apply();
        databaseKey = eulenUtils.sha(passphrase + pin);
        eulenDatabase.rekey(databaseKey, 1);
    }

    private void lockGUI() {
        editTextPassphrase.setEnabled(false);
        editTextPin.setEnabled(false);
        buttonLogin.setEnabled(false);
        buttonClear.setEnabled(false);
    }

    // if back button pressed
    @Override
    public void onBackPressed() {
        gotoMain(this);
    }

    public void postDatabase(int returnCode, Cursor cursor) {
        if(returnCode == 1) { // reopen DB with new key and return to main
            eulenDatabase.close();
            openDatabase(databaseKey);

            gotoMain(this);
        }
    }

    // place holders for interface
    public void postServer(int returnCode, JSONObject jsonObject) {

    }

    public void serverError(int returnCode, String code) {

    }
}
