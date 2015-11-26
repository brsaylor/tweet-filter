package com.bensaylor.tweetfilter;

/**
 * Data model representing a query topic.
 *
 * @author Ben Saylor
 */
public class Topic {
    int number;             // TREC microblog track topic number
    String title;           // Query string for the topic
    String queryTime;       // Date and time the query was issued
    long queryTweetTime;    // ID of oldest known relevant tweet
    long queryNewestTweet;  // ID of newest tweet to evaluate (stopping point)

    public String toString() {
        return
            "number:           " + number + "\n" +
            "title:            " + title + "\n" +
            "queryTime:        " + queryTime + "\n" +
            "queryTweetTime:   " + queryTweetTime + "\n" +
            "queryNewestTweet: " + queryNewestTweet + "\n";
    }
}
