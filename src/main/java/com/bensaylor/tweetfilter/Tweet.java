package com.bensaylor.tweetfilter;

/**
 * Data model representing an individual tweet.
 *
 * @author Ben Saylor
 */
public class Tweet {
    long id;
    String text;
    String created_at;
    boolean retweeted;
    long retweet_count;
    boolean favorited;
    User user;
    long requested_id;

    public String toString() {
        return 
            "id:               " + id + "\n" +
            "text:             " + text + "\n" +
            "created_at:       " + created_at + "\n" +
            "retweeted:        " + retweeted + "\n" +
            "retweet_count:    " + retweet_count + "\n" +
            "favorited:        " + favorited + "\n" +
            "user.id:          " + user.id + "\n" +
            "user.screen_name: " + user.screen_name + "\n" +
            "user.name:        " + user.name + "\n" +
            "requested_id:     " + requested_id + "\n";
    }
}
