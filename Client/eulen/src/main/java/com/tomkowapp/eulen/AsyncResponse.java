package com.tomkowapp.eulen;

import android.database.Cursor;
import org.json.JSONObject;

// interface to enable callbacks from EulenClient and EulenDatabase

public interface AsyncResponse {
    void postServer(int returnCode, JSONObject result);
    void serverError(int returnCode, String code);
    void postDatabase(int returnCode, Cursor cursor);
}
