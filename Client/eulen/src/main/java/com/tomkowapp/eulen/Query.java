package com.tomkowapp.eulen;

// class for storing SQLite queries and query param values

class Query {
    public String query;
    public String[] values = null;

    Query(String query) {
        this.query = query;
    }

    Query(String query, String[] values) {
        this.query = query;
        this.values = values;
    }
}
