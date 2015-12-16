package com.bensaylor.tweetfilter;

import java.io.PrintWriter;

/**
 * Baseline tweet filter.
 * This filter classifies all tweets as relevant, with a score of 1.0.
 * All other filters subclass this one.
 *
 * @author Ben Sayor
 */
public class Filter {

    protected PrintWriter log = null;

    /**
     * Reset the filter and initialize it for a new topic.
     * The baseline filter is topic-independent.
     * 
     * @param topic The topic to filter by
     */
    public void setTopic(Topic topic) {
    }

    /**
     * Assign a score and retrieval decision for the given tweet.
     *
     * @param tweet The tweet to evaluate
     * @return The score and retrieval decision
     */
    public FilterDecision decide(Tweet tweet) {
        return new FilterDecision(tweet.id, 1.0, true);
    }

    /**
     * Provide a relevance judgment (relevance feedback) to the filter.
     * This should only be called if the tweet received a positive retrieval
     * decision by the filter, or if it is the first relevant tweet for the
     * topic. The baseline filter ignores the relevance judgment.
     *
     * @param tweet The tweet for which feedback is being provided
     * @param relevance The relevance value for the tweet
     * @see Constants
     */
    public void feedback(Tweet tweet, int relevance) {
    }

    /**
     * Set the PrintWriter to be used for logging.
     *
     * @param logWriter The PrintWriter to use for logging
     */
    public void setLog(PrintWriter log) {
        this.log = log;
    }
}
