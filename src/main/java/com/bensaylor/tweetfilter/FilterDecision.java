package com.bensaylor.tweetfilter;

/**
 * Data model representing a filter decision for a specific tweet.
 *
 * @author Ben Saylor
 */
public class FilterDecision {

    public FilterDecision(long tweetId, double score, boolean retrieve) {
        this.tweetId = tweetId;
        this.score = score;
        this.retrieve = retrieve;
    }

    long tweetId;       // Tweet ID
    double score;       // Filter-specific score for the tweet
    boolean retrieve;   // Retrieval decision for the tweet
}
