package com.tomkowapp.eulen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// handles incoming NFC beams

public class beam extends baseprefs implements AsyncResponse {
    EulenDatabase eulenDatabase;
    private String[] values;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String data[] = extras.getStringArray(CONST.VALUES);
            if (data != null) {
                values = data;
                if(copyCreds()) {
                    setupDB();
                }
            }
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_beam);
    }

    @Override
    public void onPause() {
        super.onPause();

        finish();
    }

    @Override
    public void onNewIntent(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        setIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            handleIntent(getIntent());
        }
    }

    // handle incoming NFC
    private void handleIntent(Intent intent) {
            try {
                Parcelable[] rawMsgs =
                        intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                NdefMessage msgs = (NdefMessage) rawMsgs[0];
                processJSON(new String(msgs.getRecords()[0].getPayload()));
            } catch (Exception e) {
                // do nothing
            }
    }

    // process the JSON
    private void processJSON(String payload) {
        try {
            JSONArray jsonArray = new JSONArray(payload);
            List<String> list = new ArrayList<>();
            for(int i = 0; i < jsonArray.length(); i++){
                list.add(jsonArray.getString(i));
            }
            values = list.toArray(new String[list.size()]);
        } catch (Exception e) {
            // do nothing
        }
    }

    // setup the DB and check if half synced contacts exist
    private void setupDB() {
        eulenDatabase = new EulenDatabase(this, this, this);
        eulenDatabase.cleanContactsCheck(3);
    }

    // add keys to the DB
    private void commitKeysToDB() {
        if (prefs.getBoolean(CONST.PREFS_CONTACTADD, true)) {
            String id = values[values.length - 1];
            System.arraycopy(values, 0, values, 0, values.length - 1);
            eulenDatabase.receiveContact(values, id, userID, 1);
        } else {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.alert_title_error)
                    .setCancelable(false)
                    .setMessage(R.string.error_contact_overquota)
                    .setPositiveButton(getString(R.string.ok), null)
                    .show();
            final Activity activity = this;
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface arg0) {
                    gotoMain(activity);
                }
            });
        }
    }

    // log user in if not logged in or continue on if already logged in
    public void nextButton(View view) {
            Button nextButton = (Button) findViewById(R.id.buttonDone);
            nextButton.setEnabled(false);
            if (copyCreds()) {
                setupDB();
            } else { // if use is not logged in then pass keys to login intent and get login creds
                Intent intent;
                intent = new Intent(this, login.class);
                intent.putExtra(CONST.VALUES, values);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                Toast toast = Toast.makeText(
                        this,
                        getString(R.string.beam_login),
                        Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();

                finish();
            }
    }

    public void postServer(int returnCode, JSONObject result) {

    }

    public void serverError(int returnCode, String code) {

    }

    public void postDatabase(int returnCode, Cursor cursor) {
        if(returnCode == 1) { // assign keys to new contact
            if(cursor != null && cursor.moveToFirst()) {
                String exists = cursor.getString(0);
                if(exists.equals("true")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(getString(R.string.diag_keys_assigned) +
                            " " +
                            cursor.getString(1) +
                            ". " +
                            getString(R.string.contact_beam_success_OK))
                            .setCancelable(false)
                            .setNeutralButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            returnToMain();
                        }
                    }).show();
                } else if (exists.equals("false")) {
                    eulenDatabase.renameContact(cursor.getString(1), null, 2);
                }
            }
        }
        if(returnCode == 2) { // continue on to sync your info back to the sender
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.contact_beam_success) +
                    " " +
                    getString(R.string.contact_beam_success_OK))
                    .setCancelable(false).setNeutralButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    returnToMain();
                }
            }).show();
        }
        if(returnCode == 3) { // warning about existing half synced contacts
            if(cursor != null && cursor.getCount() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getString(R.string.dialog_inprog))
                        .setPositiveButton(getString(R.string.button_continue), contactAddInProgress)
                        .setNegativeButton(getString(R.string.button_cancel), contactAddInProgress).show();
            } else {
                commitKeysToDB();
            }
        }
        if(returnCode == 4) {
            commitKeysToDB();
        }
    }

    DialogInterface.OnClickListener contactAddInProgress = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    eulenDatabase.cleanContacts(4);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    finish();
                    break;
            }
        }
    };

    private void returnToMain() {
        prefs.edit().putBoolean(CONST.PREFS_NO_CONTACTS, false).apply();
        gotoMain(this);
    }
}