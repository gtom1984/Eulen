package com.tomkowapp.eulen;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// handle message list fragment

public class messageList extends ListFragment implements AsyncResponse {
    private String messageID;

    public boolean refreshing;
    public boolean listActive;
    CountDownTimer messageDeleteCounter = null;
    Toolbar toolbar;

    AlertDialog readDialog = null;

    protected ActionMode actionMode;

    private Cursor cursorMessage;
    private SimpleCursorAdapter simpleCursorAdapter;
    EulenDatabase eulenDatabase;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        listActive = getArguments().getBoolean(CONST.LIST_ACTIVE);
        refreshing = getArguments().getBoolean(CONST.REFRESHING);

        toolbar = (Toolbar) getActivity().findViewById(R.id.toolbarMain);

        eulenDatabase = new EulenDatabase(getActivity(), getActivity(), this);

        updateList();
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        setListShown(true);
    }

    public void updateList() {
        eulenDatabase.getInboxMessages(1);
    }

    protected void setSyncSafe(Boolean syncSafe) {
        ((app) this.getActivity().getApplication()).setSafeToSync(syncSafe);
    }

    // handle message click events
    public void onListItemClick(ListView l, View v, int position, long id) {
            if(actionMode != null) { // handle multi-selection actionbar select mode
                SparseBooleanArray sparseBooleanArray = getListView().getCheckedItemPositions();
                Boolean isAnythingSelected = false;
                for (int i = 0; i < getListView().getCount(); i++) {
                    if (sparseBooleanArray.get(i)) {
                        isAnythingSelected = true;
                    }
                }
                if (!isAnythingSelected) { // disable if nothing selected
                    killActionMode();
                }
            } else { // normal message clicks (not multi-select mode)
                if (listActive) {
                    messageID = ((Cursor) getListView().getItemAtPosition(position)).getString(0);
                    final String time = ((Cursor) getListView().getItemAtPosition(position)).getString(1);
                    final String flag = ((Cursor) getListView().getItemAtPosition(position)).getString(2);
                    final String contactName = ((Cursor) getListView().getItemAtPosition(position)).getString(3);
                    final String message = ((Cursor) getListView().getItemAtPosition(position)).getString(4);
                    final String photo = ((Cursor) getListView().getItemAtPosition(position)).getString(5);

                    if (flag.equals("0")) { // handle association messages
                        if (refreshing) { // block if syncing
                            Toast toast = Toast.makeText(getActivity(), getString(R.string.toast_refresh_block), Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        } else { // start contact association
                            //user message for query
                            setSyncSafe(false);
                            eulenDatabase.confirmContact(messageID, time, 4);
                        }
                    }

                    if (flag.equals("2")) { // if normal received message
                        if(photo != null && !photo.isEmpty()) { // if message is a photo
                            photoLoader photoLoader =
                                    new photoLoader(contactName, photo);
                            photoLoader.execute();
                        } else { // if message is text
                            readMessage(contactName, message, null, null);
                        }
                    }
                }
        }
    }

    // background thread photo loader
    private class photoLoader extends AsyncTask<Void, Void, Bitmap> {
        private ProgressDialog progress = new ProgressDialog(getActivity());
        private String contactName;
        private String photoID;

        photoLoader(String contactName, String photoID) {
            this.contactName = contactName;
            this.photoID = photoID;
        }

        @Override
        protected void onPreExecute() {
            progress.setTitle(getActivity().getApplicationContext().getString(R.string.photo_loading));
            progress.setMessage(getActivity().getString(R.string.progress));
            progress.setCancelable(false);
            progress.setIndeterminate(true);
            progress.show();
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                String photoBase64 = eulenDatabase.getPhoto(photoID);
                return PhotoUtils.base64toBitmap(photoBase64);
            } catch (Exception ex) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            try {
                progress.dismiss();
                if(bitmap != null) {
                    readMessage(contactName, null, bitmap, photoID); // show message to user
                } else {
                    throw new Exception();
                }
            } catch (Exception ex) {
                Toast.makeText(
                        getContext(),
                        getActivity().getString(R.string.error_photo),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    // message reader dialog
    private void readMessage(final String contactName, final String message,
                             final Bitmap photo, final String photoID) {
        if(readDialog == null) {
            listActive = false;

            final View readMessageView = View.inflate(getActivity(), R.layout.prompt_message, null);

            final AlertDialog.Builder readMessage = new AlertDialog.Builder(getActivity());

            readMessage.setView(readMessageView);

            final TextView textMessageCounter = (TextView) readMessageView.findViewById(R.id.textMessageCounter);
            final TextView textMessage = (TextView) readMessageView.findViewById(R.id.textMessage);
            final TextView textTitleMessageFrom = (TextView) readMessageView.findViewById(R.id.textTitleMessageFrom);

            // if message is a photo enable image display elements
            if(photo != null) {
                textMessage.setVisibility(View.GONE);
                final LinearLayout photoFrame =
                        (LinearLayout) readMessageView.findViewById(R.id.photoFrame);

                final ImageView photoThumb =
                        (ImageView) readMessageView.findViewById(R.id.photoThumb);
                photoThumb.setImageBitmap(photo);
                photoFrame.setVisibility(View.VISIBLE);
            } else {
                textMessage.setText(message);
            }

            textTitleMessageFrom.setText(contactName);

            // message delete counter
            messageDeleteCounter = new CountDownTimer(30000, 1000) {
                public void onTick(long millisUntilFinished) {
                    textMessageCounter.setText(String.valueOf((int) (long) (millisUntilFinished / 1000)));
                }

                public void onFinish() {
                    if (readDialog != null & readDialog.isShowing()) {
                        textMessage.setText("");
                        readDialog.dismiss();
                        readDialog = null;
                        messageDeleteCounter = null;
                    }
                    if(photo == null) {
                        eulenDatabase.deleteMessage(messageID, 2);
                    } else {
                        eulenDatabase.deletePhotoMessage(messageID, photoID, 2);
                    }
                }
            };
            messageDeleteCounter.start();

            // delete before counter and dismiss message
            readMessage
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.delete),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    messageDeleteCounter.cancel();
                                    messageDeleteCounter = null;
                                    readDialog.dismiss();
                                    readDialog = null;
                                    if(photo == null) {
                                        textMessage.setText("");
                                        eulenDatabase.deleteMessage(messageID, 2);
                                    } else {
                                        eulenDatabase.deletePhotoMessage(messageID, photoID, 2);
                                    }
                                }
                            }
                    );

            readDialog = readMessage.create();
            readDialog.show();
        }
    }

    // delete message if user leaves app and has message open
    @Override
    public void onPause() {
        if(messageDeleteCounter != null) {
            messageDeleteCounter.cancel();
            messageDeleteCounter = null;
            readDialog.dismiss();
            readDialog = null;
            eulenDatabase.deleteMessage(messageID, 2);
        }
        super.onPause();
    }

    // end multi-select mode
    private void killActionMode() {
        if(actionMode != null) {
            actionMode.finish();
        }
        actionMode = null;
        getListView().clearChoices();
        setListAdapter(simpleCursorAdapter);
        listActive = true;
        setSyncSafe(true);
    }

    public void postServer(int returnCode, JSONObject result) {

    }
    public void serverError(int returnCode, String code) {

    }

    public void postDatabase(int returnCode, final Cursor cursor) {
        if(returnCode == 1) { // messages from DB retrieved, update list
            try {
                if(cursor != null) {
                    int[] to = new int[]{R.id.listTextMessageName, R.id.listTextMessageDate, R.id.iconMessage};
                    String[] columns = new String[]{CONST.KEY_NAME, CONST.KEY_TIME, CONST.KEY_FLAG};

                    this.cursorMessage = cursor;
                    cursor.close();

                    simpleCursorAdapter = new SimpleCursorAdapter(getActivity(), R.layout.item_message, cursorMessage, columns, to, SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
                    simpleCursorAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {

                        public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                            final int flagIndex = cursor.getColumnIndex(CONST.KEY_FLAG);
                            final int flag = cursor.getInt(flagIndex);

                            if (flagIndex == columnIndex) {
                                ImageView imageView = (ImageView) view.findViewById(R.id.iconMessage);

                                switch (flag) { // display correct icon
                                    case 0:
                                        imageView.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.ic_action_add_person));
                                        break;
                                    case 2:
                                        imageView.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.ic_action_email));
                                        break;
                                }
                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
                }

                setListAdapter(simpleCursorAdapter);
                setEmptyText(getActivity().getString(R.string.textNoMessage));
                getListView().setSelector(R.drawable.selector);
                getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

                    @Override
                    public boolean onItemLongClick(AdapterView<?> arg0, View view,
                                                   int position, long id) { // long press action, sets multi-select mode for deleting multiple messages
                        if (listActive) {
                            if (refreshing) {
                                Toast toast = Toast.makeText(getActivity(), getString(R.string.toast_refresh_block), Toast.LENGTH_LONG);
                                toast.setGravity(Gravity.CENTER, 0, 0);
                                toast.show();
                                return false;
                            } else {
                                listActive = false;
                                setSyncSafe(false);

                                AppCompatActivity activity = (AppCompatActivity) getActivity();

                                actionMode = activity.startSupportActionMode(new ActionMode.Callback() {
                                    @Override
                                    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                                        actionMode.getMenuInflater().inflate(R.menu.contextual, menu);
                                        return true;
                                    }

                                    @Override
                                    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                                        return false;
                                    }

                                    @Override
                                    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                                        switch (menuItem.getItemId()) {  // multi-delete mode delete button action
                                            case R.id.action_delete:
                                                int count = getListView().getCount();
                                                List<String> listOfIDs = new ArrayList<>();
                                                List<String> listOfPhotos = new ArrayList<>();

                                                SparseBooleanArray sparseBooleanArray = getListView().getCheckedItemPositions();

                                                if (sparseBooleanArray.size() < 1) {
                                                    listActive = true;
                                                    return false;
                                                } else {
                                                    for (int i = 0; i < count; i++) {
                                                        if (sparseBooleanArray.get(i)) {
                                                            listOfIDs.add(((Cursor) getListView().getItemAtPosition(i)).getString(0)); // get IDs of messages

                                                            String photoID = ((Cursor) getListView().getItemAtPosition(i)).getString(5);
                                                            if(photoID != null && !photoID.isEmpty()) {
                                                                listOfPhotos.add(photoID); // get IDs of photos
                                                            }
                                                        }
                                                    }
                                                    listActive = false;
                                                    eulenDatabase.deleteMessageMulti(listOfIDs, listOfPhotos, 2); // delete messages and photos
                                                }
                                                killActionMode();
                                                return true;
                                        }
                                        killActionMode();
                                        return false;
                                    }

                                    @Override
                                    public void onDestroyActionMode(ActionMode actionMode) {
                                        killActionMode();
                                    }
                                });

                                getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                                getListView().setItemChecked(position, true);
                                getListView().setSelection(position);
                                getListView().setSelected(true);
                                return true;
                            }
                        } else {
                            return false;
                        }
                    }
                });

                listActive = true;
            } catch (Exception e) {
                // do nothing
            }

        }
        if(returnCode == 2) { // post rename list refresh
            updateList();
        }

        if(returnCode == 4) { // post contact association message
            if(cursor != null && cursor.moveToFirst()) {
                if(cursor.getString(0).equals(CONST.TRUE)) {  //refresh messages
                    AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                    alert.setTitle(getString(R.string.info_contactVerified));
                    alert.setMessage(getString(R.string.info_contact_keys) + " " + cursor.getString(1) + ".");
                    alert.setPositiveButton(getString(R.string.ok), null);
                    alert.show();
                }
                if(cursor.getString(0).equals("false")) {  //rename unverified user
                    String contactTime = cursor.getString(2);

                    String diagmsg = getString(R.string.info_contact_unver_start) + " " + contactTime;

                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(diagmsg).setNeutralButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                                eulenDatabase.renameContact(cursor.getString(1), "", 2);
                        }
                    }).show();
                }
                cursor.close();
            }
            setSyncSafe(true);
            updateList();
        }
    }

    @Override
    public void onDestroy() {
        if(cursorMessage != null) {
            cursorMessage.close();
        }
        super.onDestroy();
    }
}
