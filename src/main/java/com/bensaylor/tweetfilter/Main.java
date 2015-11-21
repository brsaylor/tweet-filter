package com.bensaylor.tweetfilter;

public class Main {
    public static void main(String[] args) {
        TweetDatabase db = new TweetDatabase();
        db.createTable();
        String[] jsonFilenames = {
            "../data/tweets2011/20110123/20110123-000.dat.json.gz",
            "../data/tweets2011/20110123/20110123-001.dat.json.gz"
        };
        db.importJsonFiles(jsonFilenames);
    }
}
