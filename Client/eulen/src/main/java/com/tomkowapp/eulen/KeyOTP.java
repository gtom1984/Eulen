package com.tomkowapp.eulen;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import org.json.JSONArray;
import java.util.Arrays;

// Eulen OTP key object class, stores received keys and converts to JSON and stores as NDEF message
// This is used for beaming keys from one device to another

public class KeyOTP {
    int total = 0;
    int i = 0;
    private final String userID;
    public String[] values;
    public NdefMessage ndefMessage;

    public KeyOTP(String userID, int total) {
        this.total = total;
        this.userID = userID;
        int valuesInt = (total * 3) + 3;

        values = new String[valuesInt];
    }

    public void add(final String UUID, final String KEY, int FLAG) {
        values[i++] = UUID;
        values[i++] = KEY;
        values[i++] = String.valueOf(FLAG);
    }

    public void finish() {
        try {
            //store userID and query in JSON
            JSONArray jsonArray = new JSONArray(Arrays.asList(values));
            jsonArray.put(userID);

            //convert JSON to byte form
            byte[] jsonToByte = jsonArray.toString().getBytes(); //convert json to bytes

            //compress JSON
            try {
                //build simple NDEF record for transmission
                ndefMessage = new NdefMessage(NdefRecord.createMime(CONST.NFC_MIME_BEAM, jsonToByte));

                //flip flags for local use
                for(int c = 5; c <= values.length; c = c + 3) {
                    if(values[c].equals("1")) {
                        values[c] = "2";
                    } else if(values[c].equals("2")) {
                        values[c] = "1";
                    }
                }
            } catch (Exception e) {
                // do nothing
            }
        } catch (Exception je) {
            // do nothing
        }
    }
}
