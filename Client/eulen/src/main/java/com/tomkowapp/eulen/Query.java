package com.tomkowapp.eulen;

// class for storing SQLite queries and query param values

public class Query {
    public String query;
    public String[] values = null;

    public Query(String query) {
        this.query = query;
    }

    public Query(String query, String[] values) {
        this.query = query;
        this.values = values;
    }
}
