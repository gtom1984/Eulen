package com.tomkowapp.eulen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

// Handles key creation and initiates key exchange via NFC

public class addcontact
        extends baseprefs
        implements
        AsyncResponse,
        NfcAdapter.CreateNdefMessageCallback,
        NfcAdapter.OnNdefPushCompleteCallback {

    static NfcAdapter mNfcAdapter;
    EulenDatabase eulenDatabase;
    final static int MESSAGE_SENT = 1;
    boolean cleanCheck = false;
    KeyOTP keys;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addcontact);

        checkLock(this);

        getCreds();

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.containerAddContact, getStep(1))
                    .commit();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        eulenDatabase = new EulenDatabase(this, this, this);
        eulenDatabase.cleanContactsCheck(2);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                backFunction();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private Fragment getStep(int fragStep) {
        Fragment fragment = new PlaceholderFragment();
        Bundle args = new Bundle();
        switch (fragStep) {
            case 1:
                args.putInt(CONST.STEP, R.layout.fragment_step1);
                break;
            case 2:
                args.putInt(CONST.STEP, R.layout.fragment_step2);
                break;
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onBackPressed() {
        backFunction();
    }

    public void buttonCancelContactAdd(View view) {
        backFunction();
    }

    public void doneButton(View view) {
        prefs.edit().putBoolean(CONST.PREFS_NO_CONTACTS, false).apply();
        gotoMain(this);
    }

    private void backFunction() {
        if(cleanCheck) {
           gotoMain(this);
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        return keys.ndefMessage;
    }

    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        // A handler is needed to send messages to the activity when this
        // callback occurs, because it happens from a binder thread
        mHandler.obtainMessage(MESSAGE_SENT).sendToTarget();
    }

    // handles message with weak reference to prevent potential memory leak
    private static class MyHandler extends Handler {
        private final WeakReference<addcontact> mActivity;

        private MyHandler(addcontact activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            mNfcAdapter = null;

            try {
                addcontact activity = mActivity.get();
                switch (msg.what) {
                    case MESSAGE_SENT:
                        if (activity.keys.values != null) {

                            activity.eulenDatabase.sendContact(activity.keys.values, 1);
                        } else {
                            Toast.makeText(
                                    activity,
                                    activity.getString(R.string.error_key_exchange),
                                    Toast.LENGTH_SHORT)
                                    .show();
                            activity.gotoMain(activity);
                        }
                        break;
                }
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    private final MyHandler mHandler = new MyHandler(this);


    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            int fragmentStep = getArguments().getInt(CONST.STEP);

            return inflater.inflate(fragmentStep, container, false);
        }
    }

    @Override // NFC switch
    public void onResume(){
        super.onResume();

        // ensure all is well with credentials
        getCreds();

        checkLock(this);
        setupNFC();
    }

    @Override
    public void onPause() {
        super.onPause();
        mNfcAdapter = null;
    }

    // trigger key generation
    public void keyGen() {
        SharedPreferences prefs = getSharedPreferences(CONST.PREFS, Context.MODE_PRIVATE);

        if(prefs.getBoolean(CONST.PREFS_CONTACTADD, true)) {
            keyGen task = new keyGen(this);
            task.execute();

            setupNFC();

        } else {
            AlertDialog dialog =
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.alert_title_error)
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

    private void setupNFC() {
        try {
            // Check for available NFC Adapter
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

            if (mNfcAdapter != null && mNfcAdapter.isEnabled() && mNfcAdapter.isNdefPushEnabled()) {
                // Register callback
                mNfcAdapter.setNdefPushMessageCallback(this, this);
                mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
            } else {
                Toast toast = Toast.makeText(
                        this,
                        getString(R.string.alert_nfc_on),
                        Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();

                Intent settingsNFC = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                startActivity(settingsNFC);
            }
        } catch (Exception ex) {
            // do nothing
        }
    }

    // after key generation
    public void postKeyGen(KeyOTP keys) {
        if(cleanCheck) {
            this.keys = keys;
            setupNFC();
        }
    }

    // placeholders to comply with interface
    public void postServer(int returnCode, JSONObject result) {

    }

    public void serverError(int returnCode, String code) {

    }

    public void postDatabase(int returnCode, Cursor cursor) {
        if(returnCode == 1) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.setCustomAnimations(R.animator.enter_from_left, R.animator.exit_to_right, 0, 0);
            ft.replace(R.id.containerAddContact, getStep(2)).commit();
        }
        if(returnCode == 2) {
            if(cursor != null && cursor.getCount() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getString(R.string.dialog_inprog))
                        .setPositiveButton(getString(R.string.button_continue), contactAddInProgress)
                        .setNegativeButton(getString(R.string.button_cancel), contactAddInProgress).show();
            } else {
                cleanCheck = true;
                keyGen();
            }
        }
        if(returnCode == 3) {
            cleanCheck = true;
            keyGen();
        }
    }

    DialogInterface.OnClickListener contactAddInProgress = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                        eulenDatabase.cleanContacts(3);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                        goHome();
                    break;
            }
        }
    };

    private void goHome() {
        gotoMain(this);
    }

    // create keys on background thread
    private class keyGen extends AsyncTask<Void, KeyOTP, KeyOTP> {
        ProgressDialog progress;
        Context context;

        keyGen(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(context);
            progress.setTitle(context.getString(R.string.progress_generating));
            progress.setMessage(context.getString(R.string.progress));
            progress.setCancelable(false);
            progress.setIndeterminate(true);
            progress.show();
        }

        @Override
        protected KeyOTP doInBackground(Void... params) {
            final EulenOTP eulenOTP = new EulenOTP();
            final KeyOTP keys;

            int keyAmount = getResources().getInteger(R.integer.config_key_amount);
            int keySize = getResources().getInteger(R.integer.config_key_size);

            keys = eulenOTP.generateKey(userID, keyAmount, keySize);

            return keys;
        }

        @Override
        protected void onPostExecute(KeyOTP keys) {
            try {
                if (progress != null) {
                    progress.dismiss();
                }
                postKeyGen(keys);
            } catch (Exception e) {
                // do nothing
            }
        }
    }
}
