package com.bensaylor.tweetfilter;

public class Tweet {
    long id;
    String text;
    String created_at;
    boolean retweeted;
    long retweet_count;
    boolean favorited;
    User user;
    long requested_id;
}
