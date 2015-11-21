package com.bensaylor.tweetfilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.GZIPInputStream;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteConstants;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import com.google.gson.Gson;

public class TweetDatabase {

    private SQLiteConnection db = null;
    private SQLiteStatement insertStatement = null;
    private int tweetsImported;
    private int duplicates;

    public TweetDatabase() {
        File dbfile = new File("tweets.sqlite");
        db = new SQLiteConnection(dbfile);
        try {
            db.open();
        } catch (SQLiteException e) {
            System.err.println("Error opening database file: " +
                    e.getMessage());
            db = null;
        }
    }

    public void createTable() {
        if (db == null) {
            System.err.println("Error: database is not open");
            return;
        }

        try {
            db.exec("create table tweets( " +
                    "id int primary key," +
                    "text_ text," +
                    "created_at text," +
                    "retweeted bool," +
                    "retweet_count int," +
                    "favorited bool," +
                    "user_id int," +
                    "user_screen_name text," +
                    "user_name text," +
                    "requested_id int)");
        } catch (SQLiteException e) {
            System.err.println("Error creating database: " + e.getMessage());
        }
    }

    public void importJsonFile(String filename) {
        Gson gson = new Gson();

        // Do all inserts for this file in the same transaction
        // (otherwise, inserts are very slow)
        try {
            db.exec("begin");
        } catch (SQLiteException e) {
            System.err.println(e.getMessage());
        }
 
        try (
                InputStream fileStream = new FileInputStream(filename);
                InputStream gzipStream = new GZIPInputStream(fileStream);
                Reader decoder = new InputStreamReader(gzipStream, "UTF-8");
                BufferedReader buffered = new BufferedReader(decoder)) {

            // Read each line in the file, decode the JSON on that line to a
            // Tweet object, and insert the Tweet object into the database
            while (true) {
                String line;
                try {
                    line = buffered.readLine();
                } catch (IOException e) {
                    System.err.println("Error reading " + filename);
                    line = null;
                }
                if (line == null) {
                    break;
                }

                Tweet tweet = gson.fromJson(line, Tweet.class);
                try {
                    insertTweet(tweet);
                } catch (SQLiteException e) {
                    System.err.println("Error inserting tweet: "
                            + e.getMessage());
                    return;
                }
            }

        } catch (Exception e) {
            System.err.println("Error reading " + filename + ": "
                    + e.getMessage());
        }

        try {
            db.exec("commit");
        } catch (SQLiteException e) {
            System.err.println(e.getMessage());
        }
    }
    
    public void importJsonFiles(String[] filenames) {
        tweetsImported = 0;
        duplicates = 0;

        for (int i = 0; i < filenames.length; i++) {
            System.out.println("Importing file " + (i + 1) + " of " +
                    filenames.length + ": " + filenames[i]);
            importJsonFile(filenames[i]);
            System.out.print(tweetsImported + " tweets imported, ");
            System.out.println(duplicates + " duplicate tweet IDs ignored");
        }

        if (insertStatement != null) {
            insertStatement.dispose();
            insertStatement = null;
        }
    }

    public void insertTweet(Tweet tweet) throws SQLiteException {

        // Prepare or reset insert statement
        if (insertStatement == null) {
            insertStatement = db.prepare(
                    "insert into tweets (" +
                    "id, text_, created_at, " +
                    "retweeted, retweet_count, favorited, " +
                    "user_id, user_screen_name, user_name, requested_id) " +
                    "values (" +
                    "?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        } else {
            insertStatement.reset();
        }

        // Bind tweet data to prepared insert statement
        insertStatement
            .bind(1, tweet.id)
            .bind(2, tweet.text)
            .bind(3, tweet.created_at)
            .bind(4, tweet.retweeted ? 1 : 0)
            .bind(5, tweet.retweet_count)
            .bind(6, tweet.favorited ? 1 : 0)
            .bind(7, tweet.user.id)
            .bind(8, tweet.user.screen_name)
            .bind(9, tweet.user.name)
            .bind(10, tweet.requested_id);

        // Execute the insert statement
        try {
            insertStatement.stepThrough();
            tweetsImported++;
        } catch (SQLiteException e) {

            // If we encounter a duplicate tweet ID, just record it and continue
            if (e.getErrorCode() ==
                    SQLiteConstants.SQLITE_CONSTRAINT_PRIMARYKEY) {
                duplicates++;
            } else {
                throw e;
            }
        }
    }
}
