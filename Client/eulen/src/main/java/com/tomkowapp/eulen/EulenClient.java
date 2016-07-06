package com.tomkowapp.eulen;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

// Eulen Server transfer and response logic

public class EulenClient {
    final Activity activity;
    final Context context;
    final String email;
    final AsyncResponse callback;
    final String serverPassword;

    int returnCode = 0;

    String errorCode = null;

    // with new password
    EulenClient(final Activity activity, final Context context,
                final AsyncResponse callback, final String email) {
        this.activity = activity;
        this.context = context;
        this.callback = callback;
        this.email = email;
        this.serverPassword = EulenUtils.passwordGenerator();
    }

    // with existing password
    EulenClient(final Activity activity, final Context context,
                final AsyncResponse callback, final String email, final String password) {
        this.activity = activity;
        this.context = context;
        this.callback = callback;
        this.email = email;
        this.serverPassword = password;
    }

    // handles user registration on background thread
    public void register(String reg_id, int returnCode) {
        this.returnCode = returnCode;

        HashMap<String, String> postVars = new HashMap<>();
        postVars.put(CONST.REGID, reg_id);
        serverConnector(true, CONST.REGISTER, postVars);
    }

    // user deletion on background thread
    public void delete(int returnCode) {
        this.returnCode = returnCode;

        serverConnector(true, CONST.DELETE);
    }

    // return list of messages (in main thread so wrap in async when used)
    public JSONObject list() { //optional regid to use list as a quick way to update regids
        HashMap<String, String> postVars = new HashMap<>();
        postVars.put(CONST.COMMAND, CONST.LIST);
        postVars.put(CONST.PASSWORD, serverPassword);

        return httpTransfer(email, postVars);
    }

    // same as list but updates reg_id for GCM
    public JSONObject list(String reg_id) { //optional regid to use list as a quick way to update regids
        HashMap<String, String> postVars = new HashMap<>();
        postVars.put(CONST.COMMAND, CONST.LIST);
        postVars.put(CONST.PASSWORD, serverPassword);
        postVars.put(CONST.REGID, reg_id);

        return httpTransfer(email, postVars);
    }

    // sends a message on background thread
    public void send(Message message, int returnCode) {
        this.returnCode = returnCode;

        HashMap<String, String> postVars = new HashMap<>();
        postVars.put(CONST.TO, message.to);
        postVars.put(CONST.KEY, message.UUID);
        postVars.put(CONST.DATA, message.data);

        serverConnector(true, CONST.SEND, postVars);
    }

    // sends photo on background thread
    public void sendPhoto(Message message, int returnCode) {
        this.returnCode = returnCode;

        HashMap<String, String> postVars = new HashMap<>();
        postVars.put(CONST.TO, message.to);
        postVars.put(CONST.KEY, message.UUID);
        postVars.put(CONST.PHOTO, message.data);

        serverConnector(true, CONST.SEND, postVars);
    }

    // syncs pending out going pairing message on main thread. (wrap in async)
    // Pairing messages are used for securely associating new users
    public JSONObject syncOutbox(Message message) {
        HashMap<String, String> postVars = new HashMap<>();
        postVars.put(CONST.COMMAND, CONST.SEND);
        postVars.put(CONST.PASSWORD, serverPassword);
        postVars.put(CONST.TO, message.to);
        postVars.put(CONST.KEY, message.UUID);
        postVars.put(CONST.DATA, message.data);

        return httpTransfer(email, postVars);
    }

    // erase message from server on main thread (wrap in async)
    public void erase(String messageID) {
        HashMap<String, String> postVars = new HashMap<>();
        postVars.put(CONST.COMMAND, CONST.ERASE);
        postVars.put(CONST.PASSWORD, serverPassword);
        postVars.put(CONST.MESSAGE_ID, messageID);

        httpTransfer(email, postVars);
    }

    // server connector setup
    private void serverConnector(Boolean progressOn, String command) {
        getEulenData task = new getEulenData(email, command, progressOn);
        task.execute();
    }

    private void serverConnector(Boolean progressOn, String command, HashMap<String, String> passedVars) { //run command with vars
        getEulenData task = new getEulenData(email, command, passedVars, progressOn);
        task.execute();
    }

    // get data on background thread and return JSON
    private class getEulenData extends AsyncTask<Void, JSONObject, JSONObject>{
        String email;
        String command;
        Boolean progressOn = true;
        ProgressDialog progress;
        HashMap<String, String> postVars = new HashMap<>();

        public getEulenData(String email, String command, Boolean progressOn){
            this.email = email;
            this.command = command;
            this.progressOn = progressOn;
        }

        public getEulenData(String email, String command,
                            HashMap<String, String>  postVars, Boolean progressOn){
            this.email = email;
            this.postVars = postVars;
            this.command = command;
            this.progressOn = progressOn;
        }

        @Override
        protected void onPreExecute() {
            postVars.put(CONST.COMMAND, command);

            if(!command.equals(CONST.DELETE)) {
                postVars.put(CONST.PASSWORD, serverPassword);
            }
            if(progressOn) {
                progress = new ProgressDialog(context);
                progress.setTitle(context.getString(R.string.progress_message_server));
                progress.setMessage(context.getString(R.string.progress));
                progress.setCancelable(false);
                progress.setIndeterminate(true);
                progress.show();
            }
        }

        @Override
        protected JSONObject doInBackground(Void... params) {
            return httpTransfer(email, postVars);
        }

        // handle post transfer errors and return JSON via callback
        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            try {
                if (progressOn & progress != null) {
                    progress.dismiss();
                }
            } catch (Exception ex) {
                // do nothing
            }

            boolean success;

            try {
                success = jsonObject.getBoolean(CONST.SUCCESS);
                if(command.equals(CONST.REGISTER)) {
                    jsonObject.put(CONST.PASSWORD, serverPassword);
                }
            } catch (Exception e) {
                success = false;
                if(!errorCode.equals(CONST.HTTP)) {
                    errorCode = CONST.JSON;
                }
            }

            if(success) {
                try {
                    callback.postServer(returnCode, jsonObject);
                } catch (Exception ex) {
                    // do nothing
                }
            } else {
                try {
                    errorCode = jsonObject.getString(CONST.ERROR);
                } catch (Exception e) {
                    errorCode = CONST.JSON;
                }
                if(errorCode.equals(CONST.PASSWORD)) {
                    try {
                        Toast toast =
                                Toast.makeText(
                                        context,
                                        context.getString(R.string.alert_passworderror),
                                        Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    } catch (Exception ex) {
                        // do nothing
                    }
                }
                if(errorCode.equals(CONST.DATABASE) ||
                        errorCode.equals(CONST.NODE) ||
                        errorCode.equals(CONST.DBMS)) {
                    try {
                        Toast toast = Toast.makeText(context, context.getString(R.string.alert_server_error), Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    } catch (Exception ex) {
                        // do nothing
                    }
                }
                try {
                    callback.serverError(returnCode, errorCode);
                } catch (Exception ex) {
                    // do nothing
                }
            }
        }
    }

    // http transfer method
    private JSONObject httpTransfer(String email, HashMap<String, String> postVars) {
        String token = null;
        final String serverURL = context.getString(R.string.server);

        final String scope = context.getString(R.string.oauth);

        final int REQUEST_AUTHORIZATION = 2;

        errorCode = null;

        JSONObject jsonObject = null;

        try { // get google oauth token
            token = GoogleAuthUtil.getToken(context,
                    new Account(email, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE),
                    scope);
        } catch (UserRecoverableAuthException e) {
            errorCode = CONST.AUTH;
            activity.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
        } catch (IOException|GoogleAuthException e) {
            errorCode = CONST.AUTH;
            token = null;
        }

        if(token != null) {
            // Create a new HttpClient and Post Header
            try {
                // Add token
                postVars.put(CONST.TOKEN, token);

                URL url = new URL(serverURL);

                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setReadTimeout(15000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod(CONST.POST);
                conn.setDoInput(true);
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(os, CONST.UTF8));
                writer.write(getPostDataString(postVars));

                writer.flush();
                writer.close();
                os.close();

                int responseCode=conn.getResponseCode();

                String response = "";

                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    String line;
                    BufferedReader br=
                            new BufferedReader(
                                    new InputStreamReader(conn.getInputStream()));

                    while ((line=br.readLine()) != null) {
                        response+=line;
                    }
                } else {
                    throw new Exception();
                }

                try {
                    jsonObject = new JSONObject(response);
                } catch (JSONException jsonEx) {
                    errorCode = CONST.JSON;
                }
            } catch (Exception e) {
                errorCode = CONST.HTTP;
            }
        }
        return jsonObject;
    }

    // handle returned data from server
    private String getPostDataString(
            HashMap<String, String> params)
            throws UnsupportedEncodingException {

        StringBuilder result = new StringBuilder();
        boolean first = true;
        for(Map.Entry<String, String> entry : params.entrySet()){
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), CONST.UTF8));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), CONST.UTF8));
        }

        return result.toString();
    }
}