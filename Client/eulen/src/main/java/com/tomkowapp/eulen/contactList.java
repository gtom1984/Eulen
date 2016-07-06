package com.tomkowapp.eulen;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

// contact list fragment actions and logic

public class contactList extends ListFragment implements AsyncResponse {
    public boolean listActive;
    public boolean refreshing;

    SharedPreferences prefs;

    private Cursor cursorContacts;
    AlertDialog composeDialog = null;
    private Message message;
    private String contactID;

    EulenDatabase eulenDatabase;
    EulenClient eulenClient;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        prefs = getActivity().getSharedPreferences(CONST.PREFS, Context.MODE_PRIVATE);

        listActive = getArguments().getBoolean(CONST.LIST_ACTIVE);
        refreshing = getArguments().getBoolean(CONST.REFRESHING);

        String password = ((app) this.getActivity().getApplication()).getPassword();
        String email = prefs.getString(CONST.PREFS_ACCOUNT, null);
        eulenDatabase = new EulenDatabase(getActivity(), getActivity(), this);
        eulenClient = new EulenClient(getActivity(), getActivity(), this, email, password);

        updateList();
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        setListShown(true);
    }

    // sets sync to safe, tells Eulen it's OK to sync messages, prevents conflicts with actions that interfer with sync
    protected void setSyncSafe(Boolean syncSafe) {
        ((app) this.getActivity().getApplication()).setSafeToSync(syncSafe);
    }

    public void updateList() {
        eulenDatabase.getContacts(1);
    }

    // handle contact clicks
    @Override
    public void onListItemClick(ListView l, View v, final int position, long id) {
        if(listActive) {
            final String name = ((Cursor) getListView().getItemAtPosition(position)).getString(1);
            final String user = ((Cursor) getListView().getItemAtPosition(position)).getString(2);
            contactID = String.valueOf(id);

            if (user != null) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setItems(new CharSequence[]
                                {getString(R.string.send_message), getString(R.string.rename), getString(R.string.delete), getString(R.string.button_cancel)},
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                                // The 'which' argument contains the index position
                                // of the selected item
                                switch (which) {
                                    case 0: // handle message send action
                                        if (((app) getActivity().
                                                getApplication()).getSafeToSend()) {
                                            ((app) getActivity().
                                                    getApplication()).setSafeToSend(false);
                                            final String user =
                                                    ((Cursor) getListView()
                                                            .getItemAtPosition(position))
                                                            .getString(2);
                                            final String keyCount = ((Cursor) getListView().
                                                    getItemAtPosition(position)).getString(5);
                                            int keys;
                                            if (keyCount == null) {
                                                keys = 0;
                                            } else {
                                                keys = Integer.valueOf(keyCount);
                                            }

                                            if (keys > 0) {
                                                listActive = false;

                                                final String contactName =
                                                        ((Cursor) getListView().
                                                                getItemAtPosition(position)).
                                                                getString(1);
                                                final String dbID =
                                                        ((Cursor) getListView().
                                                                getItemAtPosition(position)).
                                                                getString(7);
                                                final String uuid = ((Cursor) getListView().
                                                        getItemAtPosition(position)).
                                                        getString(3);
                                                final String key = ((Cursor) getListView().
                                                        getItemAtPosition(position)).
                                                        getString(4);
                                                quickSend(contactName, user, uuid, key, dbID);
                                            } else {
                                                AlertDialog.Builder alert =
                                                        new AlertDialog.Builder(getActivity());
                                                alert.setTitle(getString(R.string.alert_title_error));
                                                alert.setMessage(getString(R.string.no_keys));
                                                alert.setPositiveButton(getString(R.string.ok), null);
                                                alert.show();
                                                ((app) getActivity().
                                                        getApplication()).
                                                        setSafeToSend(true);
                                                listActive = true;
                                            }
                                        }
                                        break;
                                    case 1: // handle rename action
                                        eulenDatabase.renameContact(contactID, name, 2);
                                        contactID = null;
                                        break;
                                    case 2: // handle delete action
                                        listActive = false;
                                        AlertDialog.Builder builder =
                                                new AlertDialog.Builder(getActivity());
                                        builder
                                                .setMessage(getActivity().getString(R.string.confirm_contact_delete))
                                                .setPositiveButton("Yes", deleteContactListener)
                                                .setNegativeButton("No", deleteContactListener).show();
                                        break;

                                    case 3:
                                        listActive = true;
                                        break;
                                }
                            }

                        }
                );
                builder.setCancelable(true);
                builder.create().show();
            }
        }
        }

    // handle delete confirmation dialog
    DialogInterface.OnClickListener deleteContactListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    if(refreshing) { // blocks action during sync to prevent broken DB foreign keys
                        Toast toast = Toast.makeText(
                                getActivity(),
                                getString(R.string.toast_refresh_block),
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    } else {
                        setSyncSafe(false);
                        eulenDatabase.deleteContact(contactID, 2);
                        contactID = null;
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    contactID = null;
                    listActive = true;
                    break;
            }
        }
    };

    // post message send, deletes used key
    public void postServer(int returnCode, JSONObject result) {
        if(returnCode == 1) {
            eulenDatabase.deleteKey(message.keyDBID, 4);
            composeDialog = null;
            message = null;
        }
    }

    // handles network related send errors
    public void serverError(int returnCode, String code) {
        if(returnCode == 1) {
            if (code.equals(CONST.TO)) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(getString(R.string.alert_title_error));
                alert.setMessage(getString(R.string.message_to));
                alert.setPositiveButton(getString(R.string.ok), null);
                alert.show();
                ((app) getActivity().getApplication()).setSafeToSend(true);
                listActive = true;
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(
                        getString(R.string.message_fail)).
                        setPositiveButton("Retry", sendFailListener)
                        .setNegativeButton("Discard", sendFailListener).show();
            }
            if (code.equals(CONST.FULL)) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(getString(R.string.alert_title_error));
                alert.setMessage(getString(R.string.message_full));
                alert.setPositiveButton(getString(R.string.ok), null);
                alert.show();
                ((app) getActivity().getApplication()).setSafeToSend(true);
            }
        }
    }

    // dialog to handle resending if transfer fails
    DialogInterface.OnClickListener sendFailListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    listActive = false;
                    eulenClient.send(message, 1);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    listActive = true;
                    message = null;
                    composeDialog = null;
                    ((app) getActivity().getApplication()).setSafeToSend(true);
                    break;
            }
        }
    };

    public void postDatabase(int returnCode, Cursor cursor) {
        if(returnCode == 1) { // return database cursor containing list of contacts with info
            if(cursor != null) {
                try {
                    int[] to = new int[]{
                            R.id.listTextContactName,
                            R.id.listTextKeyCount,
                            R.id.listTextContactDate};
                    String[] columns =
                            new String[]{
                                    CONST.KEY_NAME,
                            CONST.KEY_COUNT,
                            CONST.KEY_TIME};

                    this.cursorContacts = cursor;

                    cursor.close();

                    setListAdapter(new SimpleCursorAdapter(
                            getActivity(),
                            R.layout.item_contact,
                            cursorContacts,
                            columns,
                            to,
                            SimpleCursorAdapter.
                                    FLAG_REGISTER_CONTENT_OBSERVER));
                    setEmptyText(getActivity().getString(R.string.textNoContacts));

                    listActive = true;
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
        if(returnCode == 2) { // disable prompt to add contact that appears when app is launched
            setSyncSafe(true);
            prefs.edit().putBoolean(CONST.PREFS_CONTACTADD, true).apply();
            updateList();
        }

        if(returnCode == 4) { // post message send (after key deleted)
            Toast toast = Toast.makeText(getActivity(), getString(R.string.message_sent), Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            updateList();
            ((app) getActivity().getApplication()).setSafeToSend(true);
        }
    }

    public void quickSend( // send message dialog
            final String contactDisplayName,
            final String sendUserIDMsg,
            final String uuidMsg,
            final String cryptoKey,
            final String dbIDMsg) {
        if(composeDialog == null) {
            final EulenOTP eulenOTP = new EulenOTP();

            View promposeComposeQuickMessageView =
                    View.inflate(getActivity(), R.layout.prompt_quick_send, null);

            AlertDialog.Builder composeDialogBuilder = new AlertDialog.Builder(
                    getActivity());

            composeDialogBuilder.setView(promposeComposeQuickMessageView);

            final EditText editTextQuickComposeMessage =
                    (EditText) promposeComposeQuickMessageView.
                            findViewById(R.id.editTextQuickComposeMessage);
            final TextView textTitleQuickComposeToDisplay =
                    (TextView) promposeComposeQuickMessageView.
                            findViewById(R.id.textTitleQuickComposeToDisplay);

            editTextQuickComposeMessage.setFilters(eulenOTP.inputFilter(getActivity()));

            textTitleQuickComposeToDisplay.setText(contactDisplayName);

            composeDialogBuilder // handle actions
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.button_send),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    String unencryptedMessage =
                                            editTextQuickComposeMessage.getText().toString();
                                    final String encrypted =
                                            eulenOTP.encrypt(unencryptedMessage,
                                                    cryptoKey.toCharArray());
                                    message =
                                            new Message(sendUserIDMsg, uuidMsg, encrypted, dbIDMsg);
                                    eulenClient.send(message, 1);
                                    composeDialog = null;
                                }
                            }
                    ).setNegativeButton(getString(R.string.button_cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            listActive = true;
                            message = null;
                            composeDialog = null;
                            ((app) getActivity().getApplication()).setSafeToSend(true);
                        }
                    }
            );

            composeDialog = composeDialogBuilder.create();
            composeDialog.show();

            editTextQuickComposeMessage.setFocusableInTouchMode(true);
            editTextQuickComposeMessage.requestFocus();
        }
    }

    @Override
    public void onDestroy() {
        if(cursorContacts != null) {
            cursorContacts.close();
        }
        super.onDestroy();
    }
}
