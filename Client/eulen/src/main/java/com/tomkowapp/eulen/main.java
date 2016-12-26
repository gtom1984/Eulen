package com.tomkowapp.eulen;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// main screen actions and logic

public class main extends baseprefs implements AsyncResponse {
    Message message;

    private int selectedFrag = 0;
    private boolean isMenuUp = false;
    private boolean refreshing = false;
    private Boolean messageListActive = true;
    private DrawerLayout drawerLayout;
    private boolean hoverButtonActive = false;
    public Toolbar toolbar;

    ImageView imageRefresh;
    Animation refreshRotation;

    private MenuItem action_refresh;

    ImageButton hoverButtonImage;

    EulenDatabase eulenDatabase = null;
    EulenClient eulenClient;
    String regid;

    Bitmap photo;
    private File photoFile;
    static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        checkLock(this); // check app lock

        toolbar = (Toolbar) findViewById(R.id.toolbarMain);
        setSupportActionBar(toolbar);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer);

        ActionBarDrawerToggle drawerToggle =
                new ActionBarDrawerToggle(
                        this,
                        drawerLayout,
                        toolbar,
                        R.string.app_name,
                        R.string.app_name);

        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        ListView mainMenuListView = (ListView) findViewById(R.id.mainOptionsMenu);
        if(mainMenuListView != null) {
            ArrayAdapter<String> mainMenuStringArray =
                    new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_list_item_1,
                            getResources().getStringArray(R.array.mainMenuOptions));
            mainMenuListView.setAdapter(mainMenuStringArray);

            mainMenuListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    switch (position) {
                        case 0:
                            menuSelection(5); //logout
                            break;
                        case 1:
                            menuSelection(6); //reset passphrase
                            break;
                        case 2:
                            menuSelection(7); //reset passphrase
                            break;
                        case 3:
                            menuSelection(8);  //delete account
                            break;
                    }
                    drawerLayout.closeDrawers();
                }
            });
        }

        if (getCreds()) { // check if logged in
            // setup objects for DB and web API
            eulenDatabase = new EulenDatabase(this, this, this);
            eulenClient = new EulenClient(this, this, this, account, password);

            setSyncSafe(true);

            hoverButtonImage = (ImageButton) findViewById(R.id.add_button);

            if (prefs.getBoolean(CONST.PREFS_NO_CONTACTS, true)) { // display add contact bar for new installs
                selectedFrag = 1;

                menuSelection(1);

                hoverButtonImage.setVisibility(View.GONE);

                FrameLayout no_contact_overlay = (FrameLayout) findViewById(R.id.nocontact_overlay);
                if(no_contact_overlay != null) {
                    no_contact_overlay.setVisibility(View.VISIBLE);
                }
            } else {
                menuSelection(0);

                sync(false);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        action_refresh = menu.findItem(R.id.action_refresh);

        isMenuUp = true;

        if (refreshing) {
            spinnerOn();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            sync(true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        this.unregisterReceiver(gcmReceiver);
    }

    @Override
    public void onResume() { // return to login if user is missing creds
        super.onResume();
        checkLock(this);

        this.registerReceiver(gcmReceiver, new IntentFilter(CONST.INTENT_MAIN));

        if (eulenDatabase == null || userID == null || password == null) {
            lockScreen(this);
            sync(false);
        }
    }

    // handle GCM messages
    private BroadcastReceiver gcmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(CONST.MESSAGE);
            if (message != null) {
                switch (message) {
                    case CONST.SYNC: // sync messages
                        if (!refreshing) {
                            if (syncSafe()) {
                                sync(true);
                            }
                        }
                        break;
                    case CONST.PASSWORD: // display alert
                        Toast toastBadPass = Toast.makeText(
                                getApplicationContext(),
                                getString(R.string.gcm_bad_password),
                                Toast.LENGTH_LONG);
                        toastBadPass.setGravity(Gravity.CENTER, 0, 0);
                        toastBadPass.show();
                        break;
                    case CONST.FULL: // display alert
                        Toast toastBoxFull = Toast.makeText(
                                getApplicationContext(),
                                getString(R.string.message_inbox_full),
                                Toast.LENGTH_LONG);
                        toastBoxFull.setGravity(Gravity.CENTER, 0, 0);
                        toastBoxFull.show();
                        break;
                }
            }
        }
    };

    public void hoverButton(View view) {
        if (selectedFrag == 0) {     // compose message hover button
            if (hoverButtonActive) { // text message
                menuSelection(2);
                hoverButtonActive = false;
                dismiss_overlay_compose(view);

                RotateAnimation anim = new RotateAnimation(
                        0f,
                        360f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f);
                anim.setInterpolator(new LinearInterpolator());
                anim.setDuration(360);
                hoverButtonImage.startAnimation(anim);
            } else { // display options for text or photo
                hoverButtonActive = true;

                final Animation animationFadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                final ImageButton photoButton = (ImageButton) findViewById(R.id.compose_photo);
                if(photoButton != null) {
                    photoButton.startAnimation(animationFadeIn);
                }

                FrameLayout compose_overlay = (FrameLayout) findViewById(R.id.compose_overlay);
                if(compose_overlay != null) {
                    compose_overlay.setVisibility(View.VISIBLE);
                }
                hoverButtonImage.setVisibility(View.GONE);
            }
        }

        if (selectedFrag == 1) { // add contact hover button
            RotateAnimation anim = new RotateAnimation(
                    0f,
                    45f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f);
            anim.setInterpolator(new LinearInterpolator());
            anim.setDuration(45);
            hoverButtonImage.startAnimation(anim);
            menuSelection(3);
        }
    }

    public void hoverButtonPhoto(View view) { // photo button
        dismiss_overlay_compose(view);

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE_SECURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE_SECURE);  // use secure camera
                String timeStamp =
                        new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(new Date());
                String filename = "eulen-temp-" + timeStamp + ".jpg";
                photoFile = new File(getExternalFilesDir(null), filename);
                Uri photoUri = Uri.fromFile(photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE); // use built in android camera app
            } catch (Exception ex) {
                Toast.makeText(this, getString(R.string.error_photo), Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    // handle camera app return from android camera
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            try {
                photoIntentAsync task = new photoIntentAsync(); // get photo on background thread
                task.execute();
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.error_photo), Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    // background thread for resizing and loading photo from file camera creates
    private class photoIntentAsync extends AsyncTask<Void, Boolean, Boolean> {
        private ProgressDialog progress = new ProgressDialog(main.this);

        @Override
        protected void onPreExecute() {
            progress.setTitle(main.this.getString(R.string.photo_loading));
            progress.setMessage(main.this.getString(R.string.progress));
            progress.setCancelable(false);
            progress.setIndeterminate(true);
            progress.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            photo = PhotoUtils.scaleImage(photoFile,
                    getResources().getInteger(R.integer.photo_height),
                    getResources().getInteger(R.integer.photo_width));

            return photoFile.delete(); // photo file delete;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            try {
                if(!result){
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.photo_delete_failure),
                            Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                eulenDatabase.getRecipients(4); // trigger send diaglog
                if (progress != null) {
                    progress.dismiss();
                }
            } catch (Exception ex) {
                // do nothing
            }
        }
    }


    private void sync(Boolean forceSync) { // trigger background sync
        if (!refreshing) {
            if (prefs.getBoolean(CONST.PREFS_OUTBOX_SYNC, false)) { // check for outgoing messages (association messages for contact adds)
                eulenDatabase.getOutboxMessages(3);
            } else {
                refreshing = true;
                fragRefreshingOn();
                listMessages(forceSync); // get normal messages from Eulen server
            }
        }
    }

    private void fragRefreshingOn() { // make fragments aware of sync state to lock elements that interfer with sync actions
        try {
            if (selectedFrag == 0) {  // if message list is active
                messageList fragment =
                        (messageList) getFragmentManager().findFragmentById(R.id.containerMain);
                if (fragment != null &&
                        fragment.isAdded() &&
                        !fragment.isDetached() &&
                        fragment.isVisible()) {
                    fragment.refreshing = true;
                }
            }
            if (selectedFrag == 1) { // if contact list is active
                contactList fragment =
                        (contactList) getFragmentManager().findFragmentById(R.id.containerMain);
                if (fragment != null &&
                        fragment.isAdded() &&
                        !fragment.isDetached() &&
                        fragment.isVisible()) {
                    fragment.refreshing = true;
                }
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    private void fragRefreshingOff() { // makes frags aware that syncs are not occurring, enables all actions in frags
        try {
            if (selectedFrag == 0) { // message frag
                messageList fragment =
                        (messageList) getFragmentManager().findFragmentById(R.id.containerMain);
                if (fragment != null &&
                        fragment.isAdded() &&
                        !fragment.isDetached() &&
                        fragment.isVisible()) {
                    fragment.refreshing = false;
                }
            }
            if (selectedFrag == 1) { // contact frag
                contactList fragment =
                        (contactList) getFragmentManager().findFragmentById(R.id.containerMain);
                if (fragment != null &&
                        fragment.isAdded() &&
                        !fragment.isDetached() &&
                        fragment.isVisible()) {
                    fragment.refreshing = false;
                }
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    // change fragment when drawer action is selected
    public void launchMessageFrag(View view) {
        drawerLayout.closeDrawers();
        menuSelection(0);
    }

    public void launchContactFrag(View view) {
        drawerLayout.closeDrawers();
        menuSelection(1);
    }

    // handle message syncing
    private void listMessages(Boolean forcesync) {
        EulenUtils eulenUtils = new EulenUtils();

        if (eulenUtils.networkConnection(this)) { // check network availability
            Boolean updated_regid = prefs.getBoolean(CONST.PREFS_REG_UPDATED, false);
            Date today = new Date(System.currentTimeMillis());
            Date lastsync;
            long rawlastsync;
            long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;
            regid = prefs.getString(CONST.PREFS_REGID, null); // get GCM reg ID

            rawlastsync = prefs.getLong(CONST.PREFS_SYNCTIME, 0);
            lastsync = new Date(rawlastsync);

            // check if last sync was too long ago or if sync was force triggered by user or action
            if (rawlastsync == 0 ||
                    Math.abs(lastsync.getTime() - today.getTime()) > MILLIS_PER_DAY ||
                    forcesync ||
                    updated_regid) {
                spinnerOn();
                syncInboxAsync task = new syncInboxAsync(forcesync, updated_regid);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR); // run sync on non-blocking background thread to allow other threads to execute
            } else {
                spinnerOff();
            }
        } else {
            refreshing = false; // set refreshing to off
        }
    }

    private void spinnerOn() { // activate spinner animation
        refreshing = true; // set sync state to refreshing
        fragRefreshingOn(); // trigger fragment awareness of new state

        if (isMenuUp) {
            imageRefresh = (ImageView) View.inflate(this, R.layout.refresh_button, null);

            refreshRotation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
            refreshRotation.setRepeatCount(Animation.INFINITE);

            imageRefresh.startAnimation(refreshRotation);

            action_refresh.setActionView(imageRefresh);
            action_refresh.setEnabled(false);
        }
    }

    private void spinnerOff() { // turn off sync animation
        if (isMenuUp && refreshRotation != null && imageRefresh != null && action_refresh != null) {
            refreshRotation.cancel();
            refreshRotation.reset();
            imageRefresh.clearAnimation();

            action_refresh.setActionView(null);
            action_refresh.setEnabled(true);
        }

        refreshing = false; // set state to not syncing
        fragRefreshingOff(); // make frags aware state is not syncing
    }

    private void menuSelection(int i) { // handle menu actions
        if (i == 0) { //message list

            // update highlighted menu option
            LinearLayout linearLayoutMessages =
                    (LinearLayout) findViewById(R.id.launchMessagesFrag);
            if(linearLayoutMessages != null) {
                linearLayoutMessages.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.ltergray));
            }
            LinearLayout linearLayoutContacts =
                    (LinearLayout) findViewById(R.id.launchContactsFrag);
            if(linearLayoutContacts != null) {
                linearLayoutContacts.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.white));
            }

            toolbar.setTitle(R.string.title_messages);

            Bundle args = new Bundle();
            Fragment messageList = new messageList();

            if (messageListActive) {  // enforce list lock
                args.putBoolean(CONST.LIST_ACTIVE, true);
            } else {
                args.putBoolean(CONST.LIST_ACTIVE, false);
            }

            if (refreshing) { // pass sync / refreshing state to fragment if being created during sync
                args.putBoolean(CONST.REFRESHING, true);
            } else {
                args.putBoolean(CONST.REFRESHING, false);
            }

            messageList.setArguments(args);

            getFragmentManager().beginTransaction()
                    .replace(R.id.containerMain, messageList)
                    .commit();

            hoverButtonImage.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_compose));

            selectedFrag = 0;
        }
        if (i == 1) { //contact list
            LinearLayout linearLayoutMessages =
                    (LinearLayout) findViewById(R.id.launchMessagesFrag);
            if(linearLayoutMessages != null) {
                linearLayoutMessages.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.white));
            }
            LinearLayout linearLayoutContacts =
                    (LinearLayout) findViewById(R.id.launchContactsFrag);
            if(linearLayoutContacts != null) {
                linearLayoutContacts.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.ltergray));
            }

            toolbar.setTitle(R.string.title_contacts);


            Bundle args = new Bundle();
            Fragment listContacts = new contactList();

            if (refreshing) {  //enforce list lock
                args.putBoolean(CONST.LIST_ACTIVE, false);
                args.putBoolean(CONST.REFRESHING, true);
            } else {
                args.putBoolean(CONST.LIST_ACTIVE, true);
                args.putBoolean(CONST.REFRESHING, false);
            }

            listContacts.setArguments(args);

            getFragmentManager().beginTransaction()
                    .replace(R.id.containerMain, listContacts)
                    .commit();

            hoverButtonImage.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_action_add_person));

            selectedFrag = 1;
        }
        if (i == 2) { // compose message
            if (((app) this.getApplication()).getSafeToSend()) {
                ((app) this.getApplication()).setSafeToSend(false);
                lockMessageList();
                eulenDatabase.getRecipients(1);
            }
        }
        if (i == 3) { // add contact
            Intent intent = new Intent(this, addcontact.class);
            startActivity(intent);
            finish();
        }
        if (i == 4) { // refresh sync
            sync(true);
        }
        if (i == 5) { // lock application
            lockScreen(this);
        }
        if (i == 6) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            String notifications_state_text;

            if(prefs.getBoolean(CONST.PREFS_NOTIFICATIONS, true)) {
                notifications_state_text = getString(R.string.enabled);
            } else {
                notifications_state_text = getString(R.string.disabled);
            }

            builder.setMessage(
                    getString(R.string.notification_text) + " " + notifications_state_text)
                    .setPositiveButton(getString(R.string.enable), notificationsListener)
                    .setNegativeButton(getString(R.string.disable), notificationsListener).show();
        }
        if (i == 7) { // re-key database
            Intent intent = new Intent(this, rekey.class);
            startActivity(intent);
            finish();
        }
        if (i == 8) { // delete Eulen Account
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(
                    getString(R.string.alert_unregister))
                    .setPositiveButton(getString(R.string.yes), deleteAccountListener)
                    .setNegativeButton(getString(R.string.no), deleteAccountListener).show();
        }
    }

    // message compose dialog
    private void messageCompose(final Cursor cursor, final Boolean photoMode) {
        final EulenOTP eulenOTP = new EulenOTP();

        View composeMessageView = View.inflate(this, R.layout.prompt_send, null);

        AlertDialog.Builder composeDialogBuilder = new AlertDialog.Builder(
                this);

        composeDialogBuilder.setView(composeMessageView);

        final EditText editTextComposeMessage =
                (EditText) composeMessageView.findViewById(R.id.editTextComposeMessage);

        if(photoMode) { // if displaying a photo instead of text
            final ImageView photoThumb =
                    (ImageView) composeMessageView.findViewById(R.id.photoThumb);
            final LinearLayout photoFrame =
                    (LinearLayout) composeMessageView.findViewById(R.id.photoFrame);
            final TextView textTitleComposeDialogMessage =
                    (TextView) composeMessageView.
                            findViewById(R.id.textTitleComposeDialogMessage);

            textTitleComposeDialogMessage.setVisibility(View.GONE);
            editTextComposeMessage.setVisibility(View.GONE);
            editTextComposeMessage.setEnabled(false);
            photoThumb.setImageBitmap(photo);
            photoFrame.setVisibility(View.VISIBLE);
            photoFrame.setEnabled(true);
        }

        final Spinner spinnerContactSelect =
                (Spinner) composeMessageView.findViewById(R.id.spinnerContactSelect);
        final TextView remainingKeys =
                (TextView) composeMessageView.findViewById(R.id.textTitleKeyCount);

        int[] to = new int[]{android.R.id.text1};
        String[] columns = new String[]{CONST.KEY_NAME};
        SimpleCursorAdapter contactSelectAdapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_spinner_item,
                cursor,
                columns,
                to,
                SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);

        contactSelectAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        spinnerContactSelect.setAdapter(contactSelectAdapter);

        // handle contact selection
        spinnerContactSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parentView, View selectedItemView, int position, long id) {
                int keyCount = ((Cursor) spinnerContactSelect.getSelectedItem()).getInt(5);

                if (keyCount <= 5) { // warn if key count is low
                    remainingKeys.setTextColor(Color.RED);
                    if (keyCount <= 1) { // warn if last key
                        Toast toast = Toast.makeText(
                                getApplicationContext(),
                                getString(R.string.alert_lastkey),
                                Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    }
                } else {
                    remainingKeys.setTextColor(Color.BLACK);
                }

                remainingKeys.setText(String.valueOf(keyCount));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });

        //filter out unusable chars from dialog
        editTextComposeMessage.setFilters(
                eulenOTP.inputFilter(this));

        // handle dialog dismiss actions
        composeDialogBuilder
                .setCancelable(false)
                .setPositiveButton(getString(R.string.button_send), // send the message
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Cursor contactSpinnerSelection;
                                String unencryptedMessage;
                                final String encryptedMessage;
                                String to;
                                String dbID;
                                String UUID;

                                char[] encryptionKey;

                                contactSpinnerSelection =
                                        ((Cursor) spinnerContactSelect.getSelectedItem());
                                to = contactSpinnerSelection.getString(2);
                                dbID = contactSpinnerSelection.getString(0);
                                UUID = contactSpinnerSelection.getString(3);
                                encryptionKey = contactSpinnerSelection.getString(4).toCharArray();

                                if(photoMode) { // if photo
                                    photoEncryptSendAsync sendPhotoTask =
                                            new photoEncryptSendAsync(to, UUID, encryptionKey, dbID); // encrypt on background thread
                                    sendPhotoTask.execute();
                                } else {
                                    unencryptedMessage = editTextComposeMessage.getText().toString();
                                    encryptedMessage =
                                            eulenOTP.encrypt(unencryptedMessage, encryptionKey); // encrypt on main
                                    editTextComposeMessage.setText("");
                                    message = new Message(to, UUID, encryptedMessage, dbID);
                                    eulenClient.send(message, 3);
                                }
                                cursor.close();
                                contactSpinnerSelection.close();
                            }
                        }
                ).setNegativeButton(getString(R.string.button_cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        cursor.close();
                        unlockMessageList();
                        ((app) getApplication()).setSafeToSend(true);
                        if(photoMode) { // cleanup by removing text or photo post when canceled
                            photo = null;
                        } else {
                            editTextComposeMessage.setText("");
                        }
                    }
                }
        );

        AlertDialog composeDialog = composeDialogBuilder.create();
        composeDialog.show();
    }

    // background thread for encrypting photo
    private class photoEncryptSendAsync extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog progress = new ProgressDialog(main.this);
        private char encryptionKey[];
        private String to;
        private String UUID;
        private String dbID;

        photoEncryptSendAsync(String to,
                                     String UUID, char[] encryptionKey, String dbID) {
            this.encryptionKey = encryptionKey;
            this.to = to;
            this.UUID = UUID;
            this.dbID = dbID;
        }

        @Override
        protected void onPreExecute() {
            progress.setTitle(main.this.getString(R.string.photo_encrypt));
            progress.setMessage(main.this.getString(R.string.progress));
            progress.setCancelable(false);
            progress.setIndeterminate(true);
            progress.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            boolean success;

            try {
                // get encrypted and encoded photo
                int quality = getResources().getInteger(R.integer.quality);
                String photoEncoded = PhotoUtils.encrypt(
                        photo,
                        String.valueOf(encryptionKey),
                        quality);
                if(photoEncoded == null) {
                    success = false;
                } else {
                    message = new Message(to, UUID, photoEncoded, dbID);
                    success = true;
                }
            } catch (Exception ex) {
                success = false;
            }

            return success;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            try {
                photo = null;
                if (progress != null) {
                    progress.dismiss();
                }
                if(success) {
                    eulenClient.sendPhoto(message, 2);
                } else {
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.photo_encrypt_error),
                            Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    // photo tap action, rotate image
    public void photoRotate(View view) {
        ImageView photoThumb = (ImageView) view.findViewById(R.id.photoThumb);
        photo = PhotoUtils.flipImage(photo);
        photoThumb.setImageBitmap(photo);
    }

    // prompt for notification choice
    DialogInterface.OnClickListener notificationsListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            SharedPreferences prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);

            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    prefs.edit().putBoolean(CONST.PREFS_NOTIFICATIONS, true).apply();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    prefs.edit().putBoolean(CONST.PREFS_NOTIFICATIONS, false).apply();
                    break;
            }
        }
    };

    // prompt for account deletion
    DialogInterface.OnClickListener deleteAccountListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    if (refreshing) {
                        Toast toast = Toast.makeText(
                                getApplicationContext(),
                                getString(R.string.toast_refresh_block),
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    } else {
                        SharedPreferences prefs = getSharedPreferences(CONST.PREFS, MODE_PRIVATE);
                        prefs.edit().clear().apply();
                        setSyncSafe(false);
                        eulenClient.delete(1);
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:

                    break;
            }
        }
    };

    // resend dialog if text message send fails
    DialogInterface.OnClickListener sendFailListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    eulenClient.send(message, 3);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    noResend();
                    break;
            }
        }
    };

    // resend dialog if photo message send fails
    DialogInterface.OnClickListener sendPhotoFailListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    eulenClient.sendPhoto(message, 2);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    noResend();
                    break;
            }
        }
    };

    // handle termination of resend
    private void noResend() {
        unlockMessageList();
        message = null;
        ((app) getApplication()).setSafeToSend(true);
    }

    // delete database method
    private void gotoDeleteDB() {
        Intent intent = new Intent(this, start.class);
        intent.setAction("");
        intent.putExtra(CONST.DELETE, CONST.DELETE);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // hide no contacts added overlay
    public void dismiss_overlay(View view) {
        FrameLayout no_contact_overlay = (FrameLayout) findViewById(R.id.nocontact_overlay);
        if(no_contact_overlay != null) {
            no_contact_overlay.setVisibility(View.GONE);
        }

        hoverButtonImage.setVisibility(View.VISIBLE);
    }

    // hide compose message type selection overlay
    public void dismiss_overlay_compose(View view) {
        FrameLayout compose_overlay = (FrameLayout) findViewById(R.id.compose_overlay);
        if(compose_overlay != null) {
            compose_overlay.setVisibility(View.GONE);
        }

        hoverButtonActive = false;
        hoverButtonImage.setVisibility(View.VISIBLE);
    }

    //public interfaces used as callbacks for server responses
    public void postServer(int returnCode, JSONObject result) {
        if (returnCode == 1) { // delete DB
            gotoDeleteDB();
        }
        if (returnCode == 2 || returnCode == 3) { // message sent successfully (text or photo)
            eulenDatabase.deleteKey(message.keyDBID, 2);
            message = null;
        }
    }

    public void serverError(int returnCode, String code) {
        if (returnCode == 1) { // delete DB even if network error
            if (code.equals(CONST.ACCOUNT)) {
                gotoDeleteDB();
            } else {
                diagError(getString(R.string.alert_delete_error));
            }
        }

        if (returnCode == 2) { // send photo failed
            sendError(code, sendPhotoFailListener);
        }

        if (returnCode == 3) { // send message failed
            sendError(code, sendFailListener);
        }
    }

    public void postDatabase(int returnCode, Cursor cursor) {
        if (returnCode == 1) { // contacts retrieved for text message, display dialog
            composeLaunch(cursor, false);
        }
        if (returnCode == 2) { // message sent successfully, post key deletion
            Toast toast = Toast.makeText(
                    this,
                    getString(R.string.message_sent),
                    Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            if (selectedFrag == 1) {
                updateFrag();
            }
            unlockMessageList();
            ((app) this.getApplication()).setSafeToSend(true);
        }
        if (returnCode == 3) { // outbox message received, sync it on background thread
            if (cursor != null) {
                syncOutboxAsync task = new syncOutboxAsync(cursor);
                task.execute();
            } else {
                listMessages(false);
            }
        }
        if (returnCode == 4) { // contacts received for photo message, display dialog
            composeLaunch(cursor, true);
        }
    }

    // start message composer
    private void composeLaunch(Cursor cursor, Boolean photoMode) {
        if (cursor != null && cursor.moveToFirst()) {
            messageCompose(cursor, photoMode);
        } else { // error if no contacts
            diagError(getString(R.string.error_nocontacts));
            ((app) this.getApplication()).setSafeToSend(true);
        }
    }

    // handle message send errors if box is full or contact no longer exists on server
    private void sendError(String code, DialogInterface.OnClickListener listener) {
        if (code.equals(CONST.TO)) {
            diagError(getString(R.string.message_to));
            ((app) this.getApplication()).setSafeToSend(true);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(
                    getString(R.string.message_fail)).
                    setPositiveButton(getString(R.string.button_retry),
                            listener)
                    .setNegativeButton(getString(R.string.button_discard),
                            listener).show();
        }
        if (code.equals(CONST.FULL)) {
            diagError(getString(R.string.message_full));
            ((app) this.getApplication()).setSafeToSend(true);
        }
    }

    // outbox sync background task (user association messages)
    private class syncOutboxAsync extends AsyncTask<Void, Boolean, Boolean> {
        Cursor cursor;
        String errorCode;
        Boolean contactDeleted = false;
        ProgressDialog progress = new ProgressDialog(main.this);

        syncOutboxAsync(Cursor cursor) {
            this.cursor = cursor;
        }

        @Override
        protected void onPreExecute() {
            progress.setTitle(getString(R.string.progress_syncing_outgoing_request));
            progress.setMessage(getString(R.string.progress));
            progress.setCancelable(false);
            progress.setIndeterminate(true);
            progress.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Boolean syncSuccess = false;
            String jsonSuccessString;

            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        JSONObject jsonObject;

                        // contact server, send association message, and get result from server
                        jsonObject = eulenClient.syncOutbox(
                                new Message(
                                        cursor.getString(3),
                                        cursor.getString(2),
                                        cursor.getString(1)));

                        // read result from server
                        jsonSuccessString = jsonObject.getString(CONST.SUCCESS);

                        // handle result
                        if (jsonSuccessString.equals(CONST.TRUE)) { // successfully sent
                            syncSuccess = true;
                        }

                        if (syncSuccess) { // if success delete outgoing message
                            eulenDatabase.deleteOutboxMessage(String.valueOf(cursor.getInt(0)));
                        } else {
                            errorCode = jsonObject.getString(CONST.ERROR);
                        }
                        if (errorCode.equals(CONST.TO)) {
                            // delete message, user is gone
                            eulenDatabase.deleteOutboxMessage(cursor.getString(0));
                            // also delete user
                            eulenDatabase.deleteOutboxContact(cursor.getString(3));

                            contactDeleted = true;

                            prefs.edit().putBoolean(CONST.PREFS_CONTACTADD, true).apply();
                        } else if (errorCode.equals(CONST.FULL)) {
                            errorCode = CONST.JSON;
                        }

                    }
                } catch (Exception e) {
                    // do nothing
                }

                if (cursor.getCount() <= 0) {
                    syncSuccess = true;
                }
                if (cursor != null) {
                    cursor.close();
                }
            } else {
                syncSuccess = true;
            }

            return syncSuccess;
        }

        @Override
        protected void onPostExecute(Boolean syncSuccess) {
            try {

                if (syncSuccess) {
                    prefs.edit().putBoolean(CONST.PREFS_OUTBOX_SYNC, false).apply(); // disable outbox sync
                } else {
                    switch (errorCode) { // display proper error for various return codes from server or general errors
                        case CONST.TO:
                            Toast toastNoExit = Toast.makeText(
                                    getApplicationContext(),
                                    getString(R.string.message_outbox_noexist),
                                    Toast.LENGTH_SHORT);
                            toastNoExit.setGravity(Gravity.CENTER, 0, 0);
                            toastNoExit.show();
                            break;
                        case CONST.FULL:
                            Toast toastFull = Toast.makeText(
                                    getApplicationContext(),
                                    getString(R.string.message_outbox_full),
                                    Toast.LENGTH_SHORT);
                            toastFull.setGravity(Gravity.CENTER, 0, 0);
                            toastFull.show();
                            break;
                        case CONST.JSON:
                            Toast toastJSON = Toast.makeText(
                                    getApplicationContext(),
                                    getString(R.string.alert_server_error),
                                    Toast.LENGTH_SHORT);
                            toastJSON.setGravity(Gravity.CENTER, 0, 0);
                            toastJSON.show();
                        case CONST.ERROR:
                            Toast toastErr = Toast.makeText(
                                    getApplicationContext(),
                                    getString(R.string.error_network),
                                    Toast.LENGTH_SHORT);
                            toastErr.setGravity(Gravity.CENTER, 0, 0);
                            toastErr.show();
                            break;
                    }
                    if (errorCode.equals(
                            CONST.DATABASE) ||
                            errorCode.equals(CONST.NODE) ||
                            errorCode.equals(CONST.DBMS)) {
                        Toast toast = Toast.makeText(
                                getApplicationContext(),
                                getString(R.string.alert_server_error),
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();

                    } else {
                        Toast toast = Toast.makeText(
                                getApplicationContext(),
                                getString(R.string.outbox_error),
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    }

                }

                if (progress != null) {
                    progress.dismiss();
                }


                if (selectedFrag == 1 & contactDeleted) { //update contact list if to contact removed
                    updateFrag();
                }

                if (cursor != null) {
                    cursor.close();
                }

                listMessages(false);
            } catch (Exception ex) {
                //do nothing
            }
        }
    }

    private void updateFrag() { // update fragments if data changes warrant it so the user sees current info
        try {
            if (selectedFrag == 0) {
                messageList fragment =
                        (messageList) getFragmentManager().findFragmentById(R.id.containerMain);
                if (fragment != null &&
                        fragment.isAdded() &&
                        !fragment.isDetached() &&
                        fragment.isVisible()) {
                    fragment.updateList();
                }
            }
            if (selectedFrag == 1) {
                contactList fragment =
                        (contactList) getFragmentManager().findFragmentById(R.id.containerMain);
                if (fragment != null &&
                        fragment.isAdded() &&
                        !fragment.isDetached() &&
                        fragment.isVisible()) {
                    fragment.updateList();
                }
            }
        } catch (Exception e) {
            // do nohing
        }
    }

    // lock the message list GUI (prevent interaction during updates)
    private void lockMessageList() {
        if (selectedFrag == 0) {
            messageList fragment =
                    (messageList) getFragmentManager().findFragmentById(R.id.containerMain);
            if (fragment != null &&
                    fragment.isAdded() &&
                    !fragment.isDetached() && fragment.isVisible()) {
                fragment.listActive = false;
                messageListActive = false;
            }
        }
    }

    // unlock the message list when actions are complete
    private void unlockMessageList() {
        if (selectedFrag == 0) {
            messageList fragment =
                    (messageList) getFragmentManager().findFragmentById(R.id.containerMain);
            if (fragment != null &&
                    fragment.isAdded() &&
                    !fragment.isDetached() &&
                    fragment.isVisible()) {
                fragment.listActive = true;
                messageListActive = true;
            }
        }
    }

    // sync messages from server to local inbox
    private class syncInboxAsync extends AsyncTask<Void, Integer, String> {
        Boolean forcesync;
        Boolean updatedRegID;

        ArrayList<String> errorList = new ArrayList<>();
        int messagesReceived = 0;

        syncInboxAsync(Boolean forcesync, Boolean updated_regid) {
            this.forcesync = forcesync;
            this.updatedRegID = updated_regid;
        }

        @Override
        protected void onProgressUpdate(Integer... values) { // update user on new message count and refresh message list as messages are added to DB
            if (selectedFrag == 0 && values[0] < 0) {
                updateFrag(); // update message list
            } else { // display new message count
                Toast toast = Toast.makeText(
                        getApplicationContext(),
                        getString(R.string.decrypting) + " " +
                                values[0] + " " +
                        getString(R.string.new_messages),
                        Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        }

        @Override
        protected String doInBackground(Void... params) {
            Boolean success;
            String code;
            String jsonData;

            JSONObject jsonObject;
            int max_messages;
            int messageCount;

            max_messages = getResources().getInteger(R.integer.config_max_messages);

            messageCount = eulenDatabase.getInboxMessageCount(); // check message count

            if (messageCount < max_messages) { // check if inbox is over quota
                if (updatedRegID) { //sync if new reg id to update GCM sender
                    jsonObject = eulenClient.list(regid);  // get data from server and update regid
                } else {
                    jsonObject = eulenClient.list(); // get data from server
                }
            } else { // stop and tell user inbox is full
                code = CONST.FULL;
                return code;
            }

            if (jsonObject != null) { // read data from server
                try {
                    success = jsonObject.getBoolean(CONST.SUCCESS); // check if success return from server
                } catch (Exception e) {
                    code = CONST.JSON;
                    return code;
                }

                if (success & updatedRegID) { // record successful regid update
                    prefs.edit().putBoolean(CONST.PREFS_REG_UPDATED, false).apply();
                }

                if (success) { // handle successful data read
                    try {
                        jsonData = jsonObject.getString(CONST.DATA);
                    } catch (JSONException e) { // stop and show error if data cannot be read
                        code = CONST.JSON;
                        return code;
                    }

                    try {
                        if (jsonData.equals(CONST.NULL)) { // if server returns no message status
                            code = CONST.NULL;
                        } else { // process downloaded messages
                            code = CONST.SUCCESS;

                            JSONArray jsonArray = jsonObject.getJSONArray(CONST.DATA); // get array of message data objects
                            publishProgress(jsonArray.length()); // display count of messages to user

                            for (int i = 0; i < jsonArray.length(); i++) { // process new messages
                                messagesReceived++;

                                String key[];

                                JSONObject row = jsonArray.getJSONObject(i);

                                // get fields for message
                                final String messageID =
                                        row.getString(CONST.ID).replaceAll("\\D+", "");
                                String time = row.getString(CONST.TIME);
                                String keyUUID = row.getString(CONST.KEY);
                                String data = row.getString(CONST.DATA);
                                String type = row.getString(CONST.TYPE);

                                // get decryption key from DB by UUID
                                key = eulenDatabase.getKey(keyUUID);

                                if (key != null &&
                                        key[0] != null &&
                                        key[1] != null &&
                                        key[2] != null) {
                                    final String KEY = key[0];
                                    final String CONTACT_ID = key[1];
                                    final String FLAG = key[2];

                                    String storeDate;

                                    try {
                                        DateFormat rawDate = new SimpleDateFormat(
                                                "yyyy-MM-DD HH:mm:ss",
                                                Locale.ENGLISH);
                                        Date date = rawDate.parse(time);
                                        DateFormat sdf = new SimpleDateFormat(
                                                "yyyy-MM-DD HH:mm:ss z",
                                                Locale.ENGLISH);
                                        sdf.setTimeZone(TimeZone.getDefault());
                                        storeDate = sdf.format(date);
                                    } catch (Exception e) {
                                        errorList.add(messagesReceived +
                                                ": " +
                                                getString(R.string.error_dateconv));
                                        storeDate = time;
                                    }

                                    Long isStored = (long)-1;

                                    if(type.equals("1")) { // decrypt and store if photo message
                                        final String photoDecrpyted =
                                                PhotoUtils.decrypt(data,
                                                        KEY);

                                        if(photoDecrpyted != null) {
                                            // store decrypted photo in encrypted database
                                            final String photoID =
                                                    String.valueOf(
                                                            eulenDatabase.
                                                                    storePhoto(photoDecrpyted));

                                            // store message meta data for photo message in message inbox
                                            isStored = eulenDatabase.storePhotoMessage(
                                                    storeDate,
                                                    CONTACT_ID,
                                                    photoID
                                            );
                                        } else { // record photo decryption error
                                            errorList.add(messagesReceived + ": " +
                                                    getString(R.string.error_decrypt));
                                        }
                                    } else { // decrypt and store text if message is text
                                        final String decrypted[] = new EulenOTP().
                                                decrypt(data, KEY.toCharArray());

                                        if (decrypted != null) {
                                            final String MESSAGE = decrypted[0];
                                            final String SIGNED = decrypted[1];

                                            if (SIGNED.equals(CONST.UNSIGNED)) { // add error to post sync error list
                                                errorList.add(messagesReceived + ": " +
                                                        getString(R.string.error_integrity));
                                            }

                                            if (FLAG.equals("0") || FLAG.equals("2")) {
                                                if (FLAG.equals("0")) {
                                                    //check if older confirms in the queue and erase
                                                    eulenDatabase.
                                                            deleteOlderConfirmationMessages(
                                                                    MESSAGE);
                                                }

                                                // store decrypted message in encrypted database
                                                isStored = eulenDatabase.storeMessage(
                                                        storeDate, MESSAGE, CONTACT_ID, FLAG);
                                            }
                                        } else {
                                            code = CONST.JSON;
                                        }
                                    }

                                    if (isStored < 0) { // delete key if stored
                                        errorList.add( // add to error list if not stored and record reason for failure
                                                messagesReceived +
                                                        ": " +
                                                        getString(R.string.error_db_store));
                                    }
                                } else { // record missing key error for error list
                                    errorList.add(messagesReceived +
                                            ": " +
                                            getString(R.string.error_key_missing));
                                }
                                eulenDatabase.deletekey(keyUUID); // delete decryption key
                                eulenClient.erase(messageID); // remove from server once added to DB successfully

                                publishProgress(-1); // refresh message list
                            }

                        }
                    } catch (JSONException e) {
                        code = CONST.JSON;
                        return code;
                    }
                } else try {
                    code = jsonObject.getString(CONST.ERROR);
                } catch (JSONException e) {
                    code = CONST.JSON;
                    return code;
                }
            } else {
                code = CONST.HTTP;
                return code;
            }

            //set sync to another 24 hours
            prefs.edit().putLong(CONST.PREFS_SYNCTIME, System.currentTimeMillis()).apply();

            return code;
        }

        @Override
        protected void onPostExecute(String code) {
            try { // provide user output after message sync if errors need to be displayed
                if (code.equals(CONST.DATABASE) ||
                        code.equals(CONST.NODE) ||
                        code.equals(CONST.DBMS)) {
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.alert_server_error),
                            Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                if (code.equals(CONST.FULL)) {
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.message_inbox_full),
                            Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                if (code.equals(CONST.HTTP)) {
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.error_network),
                            Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                if (code.equals(CONST.JSON)) {
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.error_json),
                            Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                if (code.equals(CONST.INVALID)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(main.this);
                    builder.setMessage(
                            getString(R.string.alert_reregister))
                            .setPositiveButton(getString(R.string.yes), deleteAccountListener)
                            .setNegativeButton(getString(R.string.no), deleteAccountListener)
                            .show();
                }
                if (code.equals(CONST.PASSWORD)) {
                    Toast toast = Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.alert_passworderror),
                            Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                if (code.equals(CONST.SUCCESS)) { // if message specific errors need to be shown
                    if (errorList != null && errorList.size() > 0) {
                        String toastOutput = messagesReceived + " " + getString(R.string.new_message);

                        toastOutput = toastOutput + "\n";
                        for (String errorMessage : errorList) {
                            toastOutput = toastOutput +
                                    "\n" +
                                    getString(R.string.new_message_header) +
                                    " " +
                                    errorMessage;
                        }
                        Toast toast = Toast.makeText(
                                getApplicationContext(),
                                toastOutput,
                                Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    }
                }

                spinnerOff(); // turn off spinner animation
            } catch (Exception ex) {
                //do nothing
            }
        }
    }
}
