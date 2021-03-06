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

/**
 * Interface to an SQLite database containing a subset of the Tweets2011 corpus.
 * Before the database can be used, createTable() and insertJsonFiles() must be
 * used to initialize and populate it.
 *
 * @author Ben Saylor
 */
public class TweetDatabase {
    private SQLiteConnection db = null;
    private SQLiteStatement insertStatement = null;
    private SQLiteStatement selectStatement = null;
    private SQLiteStatement fetchStatement = null;
    private SQLiteStatement existsStatement = null;
    private int tweetsImported;
    private int duplicates;

    /**
     * Open the database file, creating it if it doesn't exist.
     *
     * @param dbfile Filename of the SQLite database
     */
    public TweetDatabase(File dbfile) {

        // Open the SQLite database file, creating if it doesn't exist
        db = new SQLiteConnection(dbfile);
        try {
            db.open();
        } catch (SQLiteException e) {
            System.err.println("Error opening database file: " +
                    e.getMessage());
            db = null;
        }
    }

    /**
     * Create the 'tweets' table in the database.
     */
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

    /**
     * Import a compressed JSON file created by twitter-tools into the database.
     * Tweets with duplicate IDs are ignored.
     * 
     * @param filename Name of the *.json.gz file to import
     */
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
    
    /**
     * Import the given compressed JSON files into the database.
     *
     * @param filenames The names of the *.json.gz files to import
     */
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

    /**
     * Add a single Tweet to the database.
     *
     * @param tweet The Tweet to insert
     *
     * @throws SQLiteException
     */
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

    /**
     * Initiate a database query to retrieve tweets in ID order.
     * Tweets are retrieved by calling the next() method.
     *
     * @param id The ID of the tweet to start from
     */
    public void startFromTweetId(long id) {
        if (selectStatement != null) {
            selectStatement.dispose();
        }
        try {
            selectStatement = db.prepare(
                    "select " +
                    "id, text_, created_at, " +
                    "retweeted, retweet_count, favorited, " +
                    "user_id, user_screen_name, user_name, requested_id " +
                    "from tweets where id >= ? order by id");
            selectStatement.bind(1, id);
        } catch (SQLiteException e) {
            System.err.println("Error creating select statement: " 
                    + e.getMessage());
            return;
        }
    }

    /**
     * Fetch the next Tweet in the currently available database query results.
     * A select query must first have been initiated.
     *
     * @return The next Tweet, or null if there are no more results
     */
    public Tweet next() {
        if (selectStatement == null) {
            return null;
        }

        Tweet tweet;

        boolean rowReturned = false;
        try {
            rowReturned = selectStatement.step();
        } catch (SQLiteException e) {
            System.err.println("Error getting result row: " + e.getMessage());
        }

        if (rowReturned) {
            try {
                tweet = rowToTweet(selectStatement);
            } catch (SQLiteException e) {
                System.err.println("Error retrieving column values: " 
                        + e.getMessage());
                tweet = null;
            }
        } else {
            // No more rows
            selectStatement.dispose();
            selectStatement = null;
            tweet = null;
        }

        return tweet;
    }

    /**
     * Fetch a tweet from the database by ID.
     *
     * @param id ID of the tweet to fetch
     * @return The tweet, or null if it doesn't exist, or if there was an error
     */
    public Tweet fetchTweet(long id) {
        try {
            if (fetchStatement == null) {
                fetchStatement = db.prepare(
                        "select " +
                        "id, text_, created_at, " +
                        "retweeted, retweet_count, favorited, " +
                        "user_id, user_screen_name, user_name, requested_id " +
                        "from tweets where id = ? order by id");
            } else {
                fetchStatement.reset();
            }
            fetchStatement.bind(1, id);
            if (fetchStatement.step()) {
                return rowToTweet(fetchStatement);
            } else {
                System.err.println("Error: empty result");
                return null;
            }
        } catch (SQLiteException e) {
            System.err.println("Error fetching tweet: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check whether a tweet with the given ID exists in the database.
     *
     * @param id The tweet ID to check
     * @return true if the tweet exists in the database; false otherwise
     */
    public boolean tweetExists(long id) {
        try {
            if (existsStatement == null) {
                existsStatement = db.prepare(
                        "select exists (select * from tweets where id = ?)");
            } else {
                existsStatement.reset();
            }
            existsStatement.bind(1, id);
            if (existsStatement.step()) {
                return (existsStatement.columnInt(0) == 1);
            } else {
                System.err.println("Error: empty result");
                return false;
            }
        } catch (SQLiteException e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Instantiate and populate a Tweet from a query result row.
     *
     * @param statement A statement with a row of data ready to be read
     * @return A tweet populated with the data from the row
     *
     * @throws SQLiteException
     */
    private Tweet rowToTweet(SQLiteStatement statement) throws SQLiteException {
        Tweet tweet = new Tweet();
        tweet.user = new User();

        tweet.id = statement.columnLong(0);
        tweet.text = statement.columnString(1);
        tweet.created_at = statement.columnString(2);
        tweet.retweeted = statement.columnInt(3) == 1;
        tweet.retweet_count = statement.columnLong(4);
        tweet.favorited = statement.columnInt(5) == 1;
        tweet.user.id = statement.columnLong(6);
        tweet.user.screen_name = statement.columnString(7);
        tweet.user.name = statement.columnString(8);
        tweet.requested_id = statement.columnLong(9);

        return tweet;
    }
}
