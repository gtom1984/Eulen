package com.tomkowapp.eulen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


// handles most database interactions and provides a background thread for some methods
// a central place for all SQLite

public class EulenDatabase {
    private final Context context;
    private final Activity activity;

    //database keys
    private static final String KEY_ITEM = "item";
    private static final String KEY_VALUE = "value";
    private static final String KEY_NAME = "name";
    private static final String KEY_USERID = "userid";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_CONTACTID = "contactid";
    private static final String KEY_KEY = "key";

    private static final String TABLE_CONFIG = "config";
    private static final String TABLE_CONTACTS = "contacts";
    private static final String TABLE_KEYS = "keys";
    private static final String TABLE_INBOX = "inbox";
    private static final String TABLE_OUTBOX = "outbox";
    private static final String TABLE_PHOTOS = "photos";


    private static final String MESSAGE_DATA = "message";
    private static final String KEY_TIME = "time";
    private static final String MESSAGE_FLAG = "flag";
    private static final String KEY_ID = "_id";
    private static final String KEY_PHOTO = "photo";

    private static final String CONST_EXECSQL = "exesql";
    private static final String CONST_RAWSQL = "rawsql";
    private static final String CONST_CREATE = "create";
    private static final String CONST_DELETE = "delete";
    private static final String CONST_UPDATE = "update";

    // progress dialogs
    private static String PROG_DELETING;
    private static String PROG_STORING;
    private static String PROG_REKEYING;
    private static String PROG_CREATE;
    private static String PROG_CLEANING;
    private static String PROG_UPGRADING;

    SharedPreferences prefs;

    private AlertDialog renameDialog;

    private static final String CONST_USERID = "userid";
    private static final String CONST_PASSWORD = "password";

    int returnCode = 0;

    private static File databaseFile;
    private static SQLiteDatabase database;

    private static Query createPhotoTableQuery =
            new Query("create table " +
                    TABLE_PHOTOS +
                    " (" + KEY_ID + " integer primary key autoincrement, " + MESSAGE_DATA  + ");");
    private static Query getCreatePhotoTableQueryIndex =
            new Query("CREATE INDEX Keys_Photo on " + TABLE_PHOTOS + "("+ KEY_ID +");");

    private final AsyncResponse callback;
    private ProgressDialog progress;

    EulenDatabase(Context context, Activity activity, AsyncResponse callback) {
        this.context = context;
        this.activity = activity;
        this.callback = callback;

        databaseFile = ((app) activity.getApplication()).getDatabaseFile();

        if(databaseFile.exists()) {
            dbConnect();
        }

        prefs = context.getSharedPreferences(CONST.PREFS, Context.MODE_PRIVATE);

        PROG_DELETING = context.getString(R.string.progress_deleting);
        PROG_STORING = context.getString(R.string.progress_storing);
        PROG_REKEYING = context.getString(R.string.progress_rekeying);
        PROG_CREATE = context.getString(R.string.progress_creating);
        PROG_CLEANING = context.getString(R.string.progress_cleaning);
        PROG_UPGRADING = context.getString(R.string.progress_upgrading);

    }

    // connect to DB
    private void dbConnect() {
        database = ((app) activity.getApplication()).getDatabase();
    }

    void close() {
        if(database != null && database.isOpen()) {
            ((app) activity.getApplication()).closeDatabase();
            database = null;
        }
    }

    void deleteDB(int returnCode) {
        this.returnCode = returnCode;

        databaseOperation task = new databaseOperation(CONST_DELETE);
        task.execute();
    }

    // database creation
    void createDB(String userID, String password, String databaseKey, int returnCode) {
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("create table " + TABLE_CONFIG + " (" +
                KEY_ID + " integer primary key autoincrement, " +
                KEY_ITEM + " text not null, " +
                KEY_VALUE + " text not null);"));
        queryList.add(new Query("create table " + TABLE_CONTACTS + " (" +
                KEY_ID + " integer primary key autoincrement, " +
                KEY_TIME + ", " +
                KEY_NAME + " text not null, " +
                KEY_USERID + " integer);"));
        queryList.add(new Query("create table " + TABLE_INBOX + " " + "(" +
                KEY_ID + " integer primary key autoincrement, " +
                KEY_TIME + ", " +
                MESSAGE_DATA + ", " +
                KEY_CONTACTID + " ," +
                MESSAGE_FLAG + " integer not null, " +
                KEY_PHOTO + ", " +
                "FOREIGN KEY (" + KEY_CONTACTID + ") REFERENCES " +
                TABLE_CONTACTS + " (" + KEY_ID + ")" +
                ");"));
        queryList.add(createPhotoTableQuery);
        queryList.add(new Query("create table " + TABLE_OUTBOX + " (" +
                KEY_ID + " integer primary key autoincrement, " +
                MESSAGE_DATA + ", " +
                KEY_UUID + " ," +
                KEY_USERID + ");"));
        queryList.add(new Query("create table " + TABLE_KEYS + " (" +
                KEY_ID + " integer primary key autoincrement, " +
                KEY_UUID + " text not null, " +
                KEY_CONTACTID + " integer not null, " +
                KEY_KEY + " text not null, " +
                CONST.KEY_FLAG + " integer default 0, " +
                "FOREIGN KEY (" + KEY_CONTACTID +
                ") REFERENCES " + TABLE_CONTACTS + " (" + KEY_ID + ")" +
                ");"));
        queryList.add(new Query("CREATE INDEX Keys_UUID on " + TABLE_KEYS + "("+ KEY_UUID +");"));  //index
        queryList.add(new Query("INSERT INTO " + TABLE_CONFIG + " VALUES (null, ?, ?)",
                new String[]{CONST_USERID, userID}));
        queryList.add(new Query("INSERT INTO " + TABLE_CONFIG + " VALUES (null, ?, ?)",
                new String[]{CONST_PASSWORD, password}));
        queryList.add(getCreatePhotoTableQueryIndex);

        databaseOperation task =
                new databaseOperation(CONST_CREATE, queryList, databaseKey, PROG_CREATE);
        task.execute();
    }

    // database upgrade 1
    void upgradeDB_1 (int returnCode) {
        // migrate from version 1 to app version 18+
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("alter table " + TABLE_INBOX + " ADD COLUMN " + KEY_PHOTO + ";"));
        queryList.add(createPhotoTableQuery);
        queryList.add(getCreatePhotoTableQueryIndex);
        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, PROG_UPGRADING);
        task.execute();
    }

    // get creds for server transactions
    void getCreds(int returnCode) {
        this.returnCode = returnCode;
        final String query = "SELECT * FROM " + TABLE_CONFIG +
                " WHERE " + KEY_ITEM + " = ? OR " + KEY_ITEM + " = ?";
        final String values[] = {CONST_USERID, CONST_PASSWORD};

        databaseOperation task = new databaseOperation(CONST_RAWSQL, query, values, null);
        task.execute();
    }

    // get contact with 1st available key and info
    void getRecipients(int returnCode) {
        this.returnCode = returnCode;
        final String query =
                "SELECT " + TABLE_KEYS + "." + KEY_ID + ", " +
                        TABLE_CONTACTS + "." + KEY_NAME + ", " +
                        TABLE_CONTACTS + "." + KEY_USERID + ", " +
                        TABLE_KEYS + "." + KEY_UUID + ", " + TABLE_KEYS + "." + KEY_KEY + ", " +
                " (SELECT count(*) FROM " + TABLE_KEYS +
                        " WHERE " +
                        KEY_CONTACTID + "=" +
                        TABLE_CONTACTS + "." + KEY_ID + " AND flag=1) AS keyCount" +
                " FROM " + TABLE_CONTACTS + " INNER JOIN " +
                        TABLE_KEYS + " ON " +
                        TABLE_CONTACTS + "." + KEY_ID + "=" + TABLE_KEYS + "." + KEY_CONTACTID +
                        " AND " + TABLE_KEYS + "." + KEY_UUID + "=" +
                        "(SELECT " + TABLE_KEYS + "." + KEY_UUID +
                        " FROM " + TABLE_KEYS + " WHERE " +
                        TABLE_CONTACTS + "." + KEY_ID + "=" + TABLE_KEYS + "." + KEY_CONTACTID +
                        " AND FLAG=1 LIMIT 1) WHERE " + TABLE_CONTACTS + "." +
                        KEY_USERID + " IS NOT NULL ORDER BY " + TABLE_CONTACTS + "." + KEY_NAME;

        databaseOperation task = new databaseOperation(CONST_RAWSQL, query, null, null);
        task.execute();
    }

    // get contacts with details and first available key
    void getContacts(int returnCode) {
        this.returnCode = returnCode;
        final String query = "SELECT " + TABLE_CONTACTS + "." + KEY_ID + ", " +
                TABLE_CONTACTS + "." + KEY_NAME + ", " +
                TABLE_CONTACTS + "." + KEY_USERID + ", " +
                TABLE_KEYS + "." + KEY_UUID + ", " +
                TABLE_KEYS + "." + KEY_KEY + ", " +
                " (SELECT count(*) FROM " + TABLE_KEYS + " WHERE " +
                KEY_CONTACTID + "=" + TABLE_CONTACTS + "." + KEY_ID +
                " AND flag=1) AS keyCount, " +
                TABLE_CONTACTS + "." + KEY_TIME + ", " + TABLE_KEYS + "." + KEY_ID +
                " FROM " + TABLE_CONTACTS + " LEFT JOIN " + TABLE_KEYS + " ON " +
                TABLE_CONTACTS + "." + KEY_ID + "=" + TABLE_KEYS + "." + KEY_CONTACTID +
                " AND " + TABLE_KEYS + "." + KEY_UUID + "=" +
                "(SELECT " + TABLE_KEYS + "." + KEY_UUID +
                " FROM " + TABLE_KEYS + " WHERE " +
                TABLE_CONTACTS + "." + KEY_ID + "=" + TABLE_KEYS + "." + KEY_CONTACTID +
                " AND FLAG=1 LIMIT 1) WHERE " +
                TABLE_CONTACTS + "." + KEY_USERID +
                " IS NOT NULL ORDER BY " + TABLE_CONTACTS + "." + KEY_NAME;

        databaseOperation task = new databaseOperation(CONST_RAWSQL, query, null);
        task.execute();
    }

    // load photo from photo table
    String getPhoto(String photoID) {
        String where = KEY_ID + "=?";
        String args[] = {photoID};
        String columns[] = {MESSAGE_DATA};
        Cursor cursor = database.query(
                TABLE_PHOTOS,
                columns,
                where,
                args,
                null,
                null,
                null,
                String.valueOf(1));
        cursor.moveToFirst();
        String result = cursor.getString(0);
        cursor.close();
        return result;
    }

    // get outgoing association messages
    void getOutboxMessages(int returnCode) {
        this.returnCode = returnCode;

        final String query = "SELECT * FROM " + TABLE_OUTBOX;

        databaseOperation task = new databaseOperation(CONST_RAWSQL, query, null);
        task.execute();
    }

    // load inbox messages
    void getInboxMessages(int returnCode) {
        this.returnCode = returnCode;
        final String query = "SELECT " +
                TABLE_INBOX + "." +
                KEY_ID + ", " +
                TABLE_INBOX + "." +
                KEY_TIME + ", " +
                TABLE_INBOX + "." +
                MESSAGE_FLAG + ", " +
                TABLE_CONTACTS + "." +
                KEY_NAME + ", " +
                TABLE_INBOX + "." +
                MESSAGE_DATA + ", " +
                TABLE_INBOX + "." +
                KEY_PHOTO +
                " FROM " + TABLE_INBOX +
                " INNER JOIN " +
                TABLE_CONTACTS +
                " ON " + TABLE_CONTACTS +
                "." + KEY_ID + "=" + TABLE_INBOX +
                "." + KEY_CONTACTID + " ORDER BY " +
                TABLE_INBOX + "." + KEY_TIME + " DESC";

        databaseOperation task = new databaseOperation(CONST_RAWSQL, query, null);
        task.execute();
    }

    // get count of inbox messages
    int getInboxMessageCount() {
        Cursor cursor = database.rawQuery("SELECT * FROM " + TABLE_INBOX, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    // confirm a unassociated contact
    void confirmContact(String id, String time, int returnCode) {
        this.returnCode = returnCode;

        processContactVerification task = new processContactVerification(id, time);
        task.execute();
    }

    // delete outbox message
    void deleteOutboxMessage(String id) {
        database.delete(TABLE_OUTBOX, KEY_ID + "=?", new String[]{id});
    }

    // delete outbox placeholder contact
    void deleteOutboxContact(String id) {
        database.delete(TABLE_CONTACTS, KEY_USERID + "=?", new String[]{id});
    }

    // delete unassociated contacts
    void cleanContacts(int returnCode) {
        this.returnCode = returnCode;
        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("DELETE FROM " + TABLE_INBOX + " WHERE " +
                KEY_CONTACTID +
                " IN (SELECT " + KEY_ID +
                " FROM " + TABLE_CONTACTS +
                " WHERE " + KEY_USERID + " IS NULL);"));
        queryList.add(new Query("DELETE FROM " + TABLE_KEYS +
                " WHERE " + KEY_CONTACTID +
                " IN (SELECT " + KEY_ID +
                " FROM " + TABLE_CONTACTS + " WHERE " + KEY_USERID + " IS NULL);"));
        queryList.add(new Query("DELETE FROM " + TABLE_CONTACTS +
                " WHERE " + KEY_USERID + " IS NULL;"));
        queryList.add(new Query("DELETE FROM " + TABLE_OUTBOX + ";"));

        prefs.edit().putBoolean(CONST.PREFS_OUTBOX_SYNC, false).apply();

        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, PROG_CLEANING);
        task.execute();
    }

    // check for unassociated contacts
    void cleanContactsCheck(int returnCode) {
        this.returnCode = returnCode;
        final String query = "SELECT * FROM " + TABLE_CONTACTS +
                " WHERE " + KEY_USERID + " IS NULL";

        databaseOperation task = new databaseOperation(CONST_RAWSQL, query, null);
        task.execute();
    }

    // remove older confirmation messages from inbox
    void deleteOlderConfirmationMessages(String message) {
        //get list of duplicate confirmation messages and contacts using them
        Cursor cursor = database.query(TABLE_INBOX,
                new String[]{KEY_ID,KEY_CONTACTID},
                MESSAGE_DATA + "=? AND " + MESSAGE_FLAG + "=0",
                new String[]{message}, null, null, null);

        if(cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                database.delete(TABLE_INBOX, KEY_ID + "=?",
                        new String[] {cursor.getString(0)});
                database.delete(TABLE_CONTACTS, KEY_ID + "=?",
                        new String[] {cursor.getString(1)});
                database.delete(TABLE_KEYS, KEY_CONTACTID + "=?",
                        new String[] {cursor.getString(1)});
            }
        }
        if(cursor != null) {
            cursor.close();
        }
    }

    // get a key by it's UUID
    String[] getKey(String UUID) {
        Cursor cursor = database.rawQuery("SELECT " +
                KEY_KEY + ", " +
                KEY_CONTACTID + ", " +
                CONST.KEY_FLAG +
                " FROM " + TABLE_KEYS +
                " WHERE " + KEY_UUID + "=?", new String[]{UUID});
        String key[] = new String[3];
        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst();
            key[0] = cursor.getString(0);
            key[1] = cursor.getString(1);
            key[2] = cursor.getString(2);
            database.delete(TABLE_KEYS, KEY_UUID + "=?", new String[]{UUID});
            cursor.close();
            return key;
        } else {
            if(cursor != null) {
                cursor.close();
            }
            return null;
        }
    }

    // delete a key by it's UUID
    void deletekey(String UUID) {
        database.delete(TABLE_KEYS, KEY_UUID + "=?", new String[]{UUID});
    }

    // delete a key by it's ID
    void deleteKey(String id, int returnCode) {
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("DELETE FROM " + TABLE_KEYS + " WHERE " + KEY_ID + "=?;",
                new String[]{id}));

        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, null);
        task.execute();
    }

    // multi-delete messages
    void deleteMessageMulti(List<String> messageIDs, List<String> photoIDs, int returnCode) {
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        for (String id : messageIDs) {
            queryList.add(new Query("DELETE FROM " + TABLE_INBOX + " WHERE " + KEY_ID + "=?;",
                    new String[]{id}));
        }
        for (String id : photoIDs) {
            queryList.add(new Query("DELETE FROM " + TABLE_PHOTOS + " WHERE " + KEY_ID + "=?;",
                    new String[]{id}));
        }

        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, PROG_DELETING);
        task.execute();
    }

    // multi-delete photos
    void deletePhotoMessage(String id, String photoID, int returnCode) {
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("DELETE FROM " + TABLE_INBOX + " WHERE " + KEY_ID + "=?;",
                new String[]{id}));
        queryList.add(new Query("DELETE FROM " + TABLE_PHOTOS + " WHERE " + KEY_ID + "=?;",
                new String[]{photoID}));

        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, null);
        task.execute();
    }

    // delete single message by ID
    void deleteMessage(String id, int returnCode) {
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("DELETE FROM " + TABLE_INBOX + " WHERE " + KEY_ID + "=?;",
                new String[]{id}));

        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, null);
        task.execute();
    }

    // store a text message
    long storeMessage(String time, String data, String contactID, String flag) {
        Long insertID;
        ContentValues contentValues = new ContentValues();
        contentValues.putNull(KEY_ID);
        contentValues.put(KEY_TIME, time);
        contentValues.put(MESSAGE_DATA, data);
        contentValues.put(KEY_CONTACTID, contactID);
        contentValues.put(CONST.KEY_FLAG, flag);
        insertID = database.insert(TABLE_INBOX, null, contentValues);

        return insertID;
    }

    // store a photo message
    long storePhotoMessage(String time, String contactID, String photo) {
        Long insertID;
        ContentValues contentValues = new ContentValues();
        contentValues.putNull(KEY_ID);
        contentValues.put(KEY_TIME, time);
        contentValues.put(KEY_CONTACTID, contactID);
        contentValues.put(CONST.KEY_FLAG, 2);
        contentValues.put(CONST.PHOTO, photo);
        insertID = database.insert(TABLE_INBOX, null, contentValues);

        return insertID;
    }

    // store the photo data
    long storePhoto(String data) {
        Long insertID;
        ContentValues contentValues = new ContentValues();
        contentValues.putNull(KEY_ID);
        contentValues.put(MESSAGE_DATA, data);
        insertID = database.insert(TABLE_PHOTOS, null, contentValues);
        return insertID;
    }

    // rename a contact
    void renameContact(final String contactID,
                              final String contactName,
                              final int returnCode) {
            this.returnCode = returnCode;

            View promptContactNameView = View.inflate(activity, R.layout.prompt_contact, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    context);

            alertDialogBuilder.setView(promptContactNameView);

            final EditText editTextNameContact = (EditText) promptContactNameView
                    .findViewById(R.id.editTextNameContact);

            if (contactName != null) {
                editTextNameContact.setText(contactName);
            }

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(context.getString(R.string.button_done),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    String name = editTextNameContact.getText().toString();

                                    if (name.isEmpty() || name.length() < 1) {
                                        name = context.getString(R.string.unnamed_user);
                                    }

                                    if (contactName == null) {
                                        renameContactOperation(name, contactID);
                                    } else if (!name.equals(contactName)) {
                                        renameContactOperation(name, contactID);
                                    }
                                    renameDialog.dismiss();
                                }
                            }
                    );
            renameDialog = alertDialogBuilder.create();
            renameDialog.show();
    }

    // rename async task
    private void renameContactOperation(String name, String contactID) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(KEY_NAME, name);
        databaseOperation task =
                new databaseOperation(CONST_UPDATE, TABLE_CONTACTS, contentValues, contactID, null);
        task.execute();
    }

    // delete a contact by ID
    void deleteContact(String id, int returnCode) {
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("DELETE FROM " + TABLE_CONTACTS + " WHERE " +
                KEY_ID + "=?;", new String[]{id}));
        queryList.add(new Query("DELETE FROM " + TABLE_KEYS + " WHERE " +
                KEY_CONTACTID + "=?;", new String[]{id}));
        queryList.add(new Query("DELETE FROM " + TABLE_INBOX + " WHERE " +
                KEY_CONTACTID + "=?;", new String[]{id}));
        queryList.add(new Query("DELETE FROM " + TABLE_PHOTOS + " WHERE " +
                KEY_ID + "" +
                " IN (SELECT " + KEY_PHOTO + " FROM " + TABLE_INBOX +
                " WHERE " + KEY_CONTACTID + " = ?);", new String[]{id}));
        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, PROG_DELETING);
        task.execute();
    }

    // send a contact DB operation
    void sendContact(String values[], int returnCode) {
        this.returnCode = returnCode;

        sendContact task = new sendContact(values);
        task.execute();
    }

    // receive contact DB operation
    void receiveContact(String values[], String id, String userID, int returnCode) {
        this.returnCode = returnCode;

        receiveContact task = new receiveContact(values, id, userID);
        task.execute();
    }

    // database rekeying (passphrase and pin change)
    void rekey(String newDatabaseKey, int returnCode) {
        this.returnCode = returnCode;

        List<Query> queryList = new ArrayList<>();
        queryList.add(new Query("PRAGMA rekey = '" + newDatabaseKey + "';"));

        databaseOperation task = new databaseOperation(CONST_EXECSQL, queryList, PROG_REKEYING);
        task.execute();
    }

    // async wrapper for background DB tasks
    private class databaseOperation extends AsyncTask<Void, Cursor, Cursor> {
        String query;
        String command;
        String databaseKey;
        String[] values = null;
        String table;
        boolean fs_ok = true;
        List<Query> queryList = null;
        private ContentValues contentValues;
        private String id;
        private String progressText = null;

        databaseOperation(String command) {
            this.command = command;
        }

        databaseOperation(String command, List<Query> queryList, String progressText) {
            this.command = command;
            this.queryList = queryList;
            this.progressText = progressText;
        }

        databaseOperation(String command, List<Query> queryList, String databaseKey,
                                 String progressText) {
            this.databaseKey = databaseKey;
            this.command = command;
            this.queryList = queryList;
            this.progressText = progressText;
        }

        databaseOperation(String command,
                                 String table,
                                 ContentValues contentValues,
                                 String id,
                                 String progressText) {
            this.command = command;
            this.table = table;
            this.contentValues = contentValues;
            this.id = id;
            this.progressText = progressText;
        }

        databaseOperation(String command,
                                 String query,
                                 String[] values) {
            this.query = query;
            this.command = command;
            this.values = values;
        }

        databaseOperation(String command,
                                 String query,
                                 String[] values,
                                 String progressText) {
            this.query = query;
            this.command = command;
            this.values = values;
            this.progressText = progressText;
        }

        @Override
        protected void onPreExecute() {
            if (progressText != null) {
                progress = new ProgressDialog(context);
                progress.setTitle(progressText);
                progress.setMessage(context.getString(R.string.progress));
                progress.setCancelable(false);
                progress.setIndeterminate(true);
                progress.show();
            }
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            Cursor cursor = null;

                if (command.equals(CONST_CREATE)) {
                    fs_ok = databaseFile.mkdirs();
                    fs_ok = databaseFile.delete();
                    ((app) activity.getApplication()).openDatabase(databaseKey);
                    dbConnect();
                }

                if (command.equals(CONST_DELETE)) {
                    if(database != null && database.isOpen()){
                        close();
                    }

                    fs_ok = databaseFile.delete();
                }

                if(database != null && database.isOpen()) {
                    if (command.equals(CONST_RAWSQL)) {
                        cursor = database.rawQuery(query, values);
                    }

                    if (command.equals(CONST_UPDATE)) {
                        database.update(table, contentValues, KEY_ID + "=?", new String[]{id});
                    }

                    if (command.equals(CONST_EXECSQL) || command.equals(CONST_CREATE)) {
                        for (int i = 0; i < queryList.size(); i++) {
                            Query queryItem = queryList.get(i);
                            if (queryItem.values == null) {
                                database.execSQL(queryItem.query);
                            } else {
                                database.execSQL(queryItem.query, queryItem.values);
                            }
                        }
                    }
                }

            MatrixCursor matrixCursor = new EulenUtils().copyCursor(cursor);

            if (!command.equals(CONST_DELETE)) {
                if(cursor != null) {
                    cursor.close();
                }
            }

            return matrixCursor;
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            try {
                if (progressText != null & progress != null) {
                    progress.dismiss();
                }
            } catch (Exception ex) {
                // do nothing
            }
            if(!fs_ok) {
                try {
                    Toast.makeText(activity,
                            context.getString(R.string.error_filesystem),
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    // do nothing
                }
            }
            try {
                callback.postDatabase(returnCode, cursor);
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    // background task for send contact DB operations
    private class sendContact extends AsyncTask<Void, Void, Void> {
        String[] values = null;

        sendContact(String values[]) {
            this.values = values;
        }

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(context);
            progress.setTitle(PROG_STORING);
            progress.setMessage(context.getString(R.string.progress));
            progress.setCancelable(false);
            progress.setIndeterminate(true);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            ContentValues contentValues = new ContentValues();
            contentValues.putNull(KEY_ID);
            contentValues.put(KEY_NAME,
                    context.getString(R.string.unverified_user));
            contentValues.put(KEY_TIME,
                    new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH).format(new Date()));
            contentValues.putNull(KEY_USERID);
            String dbid = String.valueOf(database.insert(TABLE_CONTACTS,
                    null,
                    contentValues));  //store dummy user

            bulkKeyStore(0, dbid, values);

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            values = null;
            try {
                if (progress != null) {
                    progress.dismiss();
                }
            } catch (Exception ex) {
                // do nothing
            }
            try {
                callback.postDatabase(returnCode, null);
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    // receive contact background operation
    private class receiveContact extends AsyncTask<Void, Cursor, Cursor> {
        String lookupUserID;
        String userID;
        String[] values;

        receiveContact(String values[], String lookupUserID, String userID) {
                this.values = values;
                this.lookupUserID = lookupUserID.replaceAll("\\D+","");
                this.userID = userID.replaceAll("\\D+","");
            }

            @Override
            protected void onPreExecute() {
                progress = new ProgressDialog(context);
                progress.setTitle(PROG_STORING);
                progress.setMessage(context.getString(R.string.progress));
                progress.setCancelable(false);
                progress.setIndeterminate(true);
                progress.show();
            }

            @Override
            protected Cursor doInBackground(Void... params) {
                MatrixCursor matrixCursor = null;

                if (database != null && database.isOpen()) {
                    final String CONST_EXISTS = "exists";

                    String dbid[] = getContactByUSERID(lookupUserID); // see if contact exists

                    // if contact doesn't match and existing contact then create a fresh contact
                    if (dbid == null) {
                        dbid = new String[1];

                        ContentValues contentValues = new ContentValues();
                        contentValues.putNull(KEY_ID);
                        contentValues.put(KEY_NAME, context.getString(R.string.unnamed_user));
                        contentValues.put(KEY_TIME,
                                new SimpleDateFormat("dd MMM yyyy HH:mm z",
                                        Locale.ENGLISH).format(new Date()));
                        contentValues.put(KEY_USERID, lookupUserID);
                        dbid[0] = String.valueOf(database.insert(TABLE_CONTACTS,
                                null,
                                contentValues));

                        String[] columns = new String[]{CONST_EXISTS, KEY_ID};
                        MatrixCursor mcContactID = new MatrixCursor(columns);
                        mcContactID.addRow(new Object[]{"false", dbid[0]});
                        matrixCursor = mcContactID;
                    } else { // add to existing contact
                        String[] columns = new String[]{CONST_EXISTS, KEY_NAME};
                        MatrixCursor mcContactName = new MatrixCursor(columns);
                        mcContactName.addRow(new Object[]{"true", dbid[1]});
                        matrixCursor = mcContactName;
                        database.delete(TABLE_KEYS,
                                KEY_CONTACTID + "=?",
                                new String[]{dbid[0]});  //delete old keys
                    }

                    // create association message to provide sender this user's user ID
                    String outboxMessage = new EulenOTP().encrypt(userID, values[1].toCharArray());
                    ContentValues contentValuesOutbox = new ContentValues();
                    contentValuesOutbox.putNull(KEY_ID);
                    contentValuesOutbox.put(MESSAGE_DATA, outboxMessage);
                    contentValuesOutbox.put(KEY_UUID, values[0]);
                    contentValuesOutbox.put(KEY_USERID, lookupUserID);

                    database.insert(TABLE_OUTBOX, null, contentValuesOutbox);

                    prefs.edit().putBoolean(CONST.PREFS_OUTBOX_SYNC, true).apply();

                    bulkKeyStore(3, dbid[0], values); // bulk DB operation for speed
                }
            return matrixCursor;
        }

        @Override
        protected void onPostExecute(Cursor dbid) {
            values = null;
            try {
                if (progress != null) {
                    progress.dismiss();
                }
            } catch (Exception ex) {
                // do nothing
            }
            try{
                callback.postDatabase(returnCode, dbid);
            } catch (Exception ex) {
                // do nothing
            }
        }
    }

    // bulk key storage for faster DB operation
    private void bulkKeyStore(int c, String dbid, String values[]) {
        if(database !=null && database.isOpen()) {
            SQLiteStatement statement =
                    database.compileStatement("INSERT INTO " +
                            TABLE_KEYS + " VALUES (null, ?, ?, ?, ?);");

            database.beginTransaction();

            while (c < (values.length - 1)) {
                statement.clearBindings();
                statement.bindString(1, values[c]);
                statement.bindString(2, dbid);
                statement.bindString(3, values[c + 1]);
                statement.bindString(4, values[c + 2]);
                statement.execute();
                c = c + 3;
            }

            database.setTransactionSuccessful();
            database.endTransaction();


            //disable adding contact if over
            Cursor contactCount = database.rawQuery("SELECT * FROM " + TABLE_CONTACTS, null);
            int max_contacts = context.getResources().getInteger(R.integer.config_max_contacts);

            if (contactCount.getCount() >= max_contacts) {
                prefs.edit().putBoolean(CONST.PREFS_CONTACTADD, false).apply();
            }
            contactCount.close();
        }
    }

    // get a contact by their user ID
    private String[] getContactByUSERID(String userID) {
        String result[] = new String[2];
        if(database != null && database.isOpen()) {

            userID = userID.replaceAll("\\D+", ""); //remove all non-numbers

            Cursor cursor = database.query(TABLE_CONTACTS,
                    new String[]{KEY_ID, KEY_NAME},
                    KEY_USERID + "=?",
                    new String[]{String.valueOf(userID)}, null, null, null);

            if (cursor != null && cursor.moveToFirst() && cursor.getCount() > 0) {
                cursor.moveToFirst();
                result[0] = cursor.getString(0);
                result[1] = cursor.getString(1);
            } else {
                try {
                    if (cursor != null) {
                        cursor.close();
                    }
                } catch (Exception e) {
                    // do nothing
                }
                return null;
            }

            cursor.close();

        }

        return result;
    }

    // handle the verification process for an association message
    private class processContactVerification extends AsyncTask<Void, Cursor, Cursor> {
        String messageID;
        String messageTime;

        processContactVerification(String messageID, String messageTime) {
            this.messageID = messageID;
            this.messageTime = messageTime;
        }

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(context);
            progress.setTitle(context.getString(R.string.progress_contact));
            progress.setMessage(context.getString(R.string.progress));
            progress.setCancelable(false);
            progress.setIndeterminate(true);
            progress.show();
        }

        @Override
        protected Cursor doInBackground(Void... params) {

            MatrixCursor matrixCursor = null;
            if(database != null && database.isOpen()) {

                String localDBcontactID = null;
                String remoteServerDBID = null;

                String userLookup[];
                Cursor message;

                message = database.rawQuery("SELECT " + KEY_CONTACTID + ", " +
                        MESSAGE_DATA + " FROM " + TABLE_INBOX +
                        " WHERE " + KEY_ID + "=?", new String[]{messageID});

                if (message != null && message.moveToFirst()) {
                    message.moveToFirst();
                    localDBcontactID = message.getString(0);
                    remoteServerDBID = message.getString(1);
                    message.close();
                }

                if (localDBcontactID != null & remoteServerDBID != null) {  // do we know this user?
                    //see if user is already in DB
                    userLookup = getContactByUSERID(remoteServerDBID);

                    if (userLookup != null) {
                        //add update keys to point to existing user and delete placeholder unverified user
                        String contactDBID = userLookup[0];

                        database.delete(TABLE_KEYS, KEY_CONTACTID + "=?",
                                new String[]{contactDBID});  //delete keys for found user

                        ContentValues contentValues = new ContentValues();
                        contentValues.put(KEY_CONTACTID, contactDBID);
                        database.update(TABLE_KEYS, contentValues, KEY_CONTACTID + "=?",
                                new String[]{localDBcontactID});  //update new keys to use found user and not temp user

                        database.delete(TABLE_CONTACTS, KEY_ID + "=?",
                                new String[]{localDBcontactID});  //delete temp user

                        matrixCursor = new MatrixCursor(new String[]{"exists", "dbid"});
                        matrixCursor.addRow(new Object[]{"true", userLookup[1]});
                    } else {
                        //add userID to unverified user and pass false for exists
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(KEY_USERID, remoteServerDBID);
                        database.update(TABLE_CONTACTS, contentValues, KEY_ID + "=?",
                                new String[]{String.valueOf(localDBcontactID)});  //add eulen server id to existing contact

                        matrixCursor = new MatrixCursor(new String[]{"exists", "dbid", "time"});
                        matrixCursor.addRow(new Object[]{"false", localDBcontactID, messageTime});
                    }
                } else {
                    matrixCursor = null;
                }

                database.delete(TABLE_INBOX, KEY_ID + "=?", new String[]{messageID});  //delete message
            }
            return matrixCursor;
        }

        @Override
        protected void onPostExecute(Cursor result) {
            try {
                if (progress != null) {
                    progress.dismiss();
                }
            } catch (Exception ex) {
                // do nothing
            }
            try {
                callback.postDatabase(returnCode, result);
            } catch (Exception ex) {
                // do nothing
            }
        }
    }
}
