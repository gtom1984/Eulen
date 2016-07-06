package com.tomkowapp.eulen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Toast;

import java.io.IOException;

// base class for NFC tag classes

public class nfctagbase extends baseprefs {
    protected String passphrase;
    IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;

    // start NFC
    public void startNFC(Activity activity) {
        pendingIntent = PendingIntent.getActivity(activity, 0, new Intent(activity, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP), 0);
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity);

        if(nfcAdapter == null) {
            Toast toast = Toast.makeText(this, getString(R.string.alert_nfc_no), Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            finish();
            System.exit(0);
        } else if(!nfcAdapter.isEnabled()) {
            Toast toast = Toast.makeText(this, getString(R.string.alert_nfc_on), Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            Intent settingsNFC = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            startActivity(settingsNFC);
        }
    }

    @Override //NFC switch
    public void onResume(){
        super.onResume();
        enableNfcWrite();
        postResume();
    }

    @Override //NFC switch
    public void onPause(){
        super.onPause();
        disableNfcWrite();
        postPause();
    }

    @Override
    public void onNewIntent(Intent intent) { //handle NFC tag swipe
        handleIntent(intent);
    }

    // handle NFC write event
    protected void handleIntent(Intent intent) {
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag discoveredTag;
            discoveredTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            try {
                if(discoveredTag != null){
                    if(validate()) {
                        writeTAG(discoveredTag);
                        postNFC();
                    }
                }
            } catch (Exception e) {
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle(getString(R.string.alert_title_error));
                alert.setMessage(getString(R.string.alert_error_nfc));
                alert.setPositiveButton(getString(R.string.ok), null);
                alert.show();
            }
        }
    }

    // place holder for validation method (override in sub classes)
    protected boolean validate() {
        return false;
    }

    // place holder for post NFC, override in base class
    protected void postNFC() {

    }
    protected void postPause() {

    }
    protected void postResume() {

    }

    protected void writeTAG(Tag tag) throws IOException, FormatException {
        Ndef ndefTag = Ndef.get(tag);
        byte[] stringBytes = passphrase.getBytes();

        NdefRecord data = NdefRecord.createMime(CONST.NFC_MIME_LOGIN, stringBytes);
        NdefMessage message = new NdefMessage(data);

        if (ndefTag != null) { //write to formatted tag
            ndefTag.connect();
            ndefTag.writeNdefMessage(message);
        } else { //format the tag
            NdefFormatable format = NdefFormatable.get(tag);
            if(format != null) {
                format.connect();
                format.format(message);
            }
        }
    }

    protected void enableNfcWrite(){ //turn on NFC
        IntentFilter[] tagFilters = new IntentFilter[] { tagDetected };
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, tagFilters, null);
    }

    protected void disableNfcWrite(){  //turn off NFC
        nfcAdapter.disableForegroundDispatch(this);
    } // NFC off
}
