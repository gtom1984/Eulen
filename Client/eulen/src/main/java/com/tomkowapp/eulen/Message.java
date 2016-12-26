package com.tomkowapp.eulen;

// class to store message objects

public class Message {
    public String to;
    String UUID;
    public String data;
    String keyDBID;

    public Message(String to, String UUID, String data) {
        this.to = to;
        this.UUID = UUID;
        this.data = data;
    }

    public Message(String to, String UUID, String data, String keyDBID) {
        this.to = to.replaceAll("\\D+","");
        this.UUID = UUID;
        this.data = data;
        this.keyDBID = keyDBID;
    }
}
