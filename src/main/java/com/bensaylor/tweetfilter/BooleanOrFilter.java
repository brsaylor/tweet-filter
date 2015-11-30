package com.bensaylor.tweetfilter;

import java.util.HashSet;

import weka.core.tokenizers.WordTokenizer;

/**
 * Filter that retrieves all tweets containing any of the terms in the query.
 *
 * @author Ben Saylor (brsaylor@gmail.com)
 */
public class BooleanOrFilter extends Filter {

    private WordTokenizer tokenizer;
    private HashSet<String> query;

    public BooleanOrFilter() {
        tokenizer = new WordTokenizer();
    }

    @Override
    public void setTopic(Topic topic) {
        query = new HashSet<>();
        tokenizer.tokenize(topic.title);
        while (tokenizer.hasMoreElements()) {
            query.add(tokenizer.nextElement());
        }
    }

    @Override
    public FilterDecision decide(Tweet tweet) {
        tokenizer.tokenize(tweet.text);
        while (tokenizer.hasMoreElements()) {
            if (query.contains(tokenizer.nextElement())) {
                return new FilterDecision(tweet.id, 1.0, true);
            }
        }
        return new FilterDecision(tweet.id, 0.0, false);
    }
}
