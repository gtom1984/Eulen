package com.tomkowapp.eulen;

import android.app.Application;
import net.sqlcipher.database.SQLiteDatabase;
import java.io.File;

// application level, to be referenced by various activities

public class app extends Application {
    private boolean lockstate;
    private String userID;
    private String password;
    private static SQLiteDatabase database = null;
    private static final String DB_FILE = "eulen.db";
    private static File databaseFile = null;
    private boolean safeToSync = true;
    public boolean safeToSend = true;

    public boolean getLockstate() {
        return lockstate;
    }

    public void setLockstate(boolean lockstate) {
        this.lockstate = lockstate;
    }

    public void setSafeToSync(boolean safeToSync) {
        this.safeToSync = safeToSync;
    }

    public void setSafeToSend(boolean safeToSend) {
        this.safeToSend = safeToSend;
    }

    public void setCreds(String userID, String password) {
        this.userID = userID;
        this.password = password;
    }

    // return database from application to maintain one single connection, improved performance
    // and stability
    public SQLiteDatabase getDatabase() {
        return database;
    }

    public void openDatabase(String databaseKey) {
            SQLiteDatabase.loadLibs(getApplicationContext());
            database = SQLiteDatabase.openOrCreateDatabase(
                    getApplicationContext()
                            .getDatabasePath(DB_FILE), databaseKey, null);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        closeDatabase();
    }
    public void closeDatabase() {
        if(database != null) {
            database.close();
            database = null;
        }
    }

    public File getDatabaseFile() {
        if(databaseFile == null) {
            databaseFile = getApplicationContext().getDatabasePath(DB_FILE);
        }
        return databaseFile;
    }

    public Boolean getSafeToSync() {
        return safeToSync;
    }

    public Boolean getSafeToSend() {
        return safeToSend;
    }

    public String getUserID() {
        return userID;
    }

    public String getPassword() {
        return password;
    }

}