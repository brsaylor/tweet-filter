package com.bensaylor.tweetfilter;

import java.util.HashMap;
import java.util.HashSet;

import weka.core.tokenizers.WordTokenizer;

// Using org.tartarus.snowball directly instead of
// weka.core.stemmers.SnowballStemmer, which causes java.util.zip.ZipException
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.porterStemmer;

/**
 * Simple query-based filter
 *
 * @author Ben Sayor
 */
public class QueryFilter extends Filter {

    private WordTokenizer tokenizer;
    private SnowballStemmer stemmer;
    private HashSet<String> query;

    public QueryFilter() {
        tokenizer = new WordTokenizer();
        stemmer = new porterStemmer();
    }

    @Override
    public void setTopic(Topic topic) {
        query = new HashSet<>();
        tokenizer.tokenize(topic.title);
        while (tokenizer.hasMoreElements()) {
            query.add(normalize(tokenizer.nextElement()));
        }
    }

    @Override
    public FilterDecision decide(Tweet tweet) {

        // There are no tweets starting with RT that are judged relevant in the
        // training set. "RT" typically indicates a retweet with no information
        // beyond the original tweet.
        if (tweet.text.startsWith("RT")) {
            return new FilterDecision(tweet.id, 0.0, false);
        }

        tokenizer.tokenize(tweet.text);
        HashMap<String,Integer> tfByTerm = new HashMap<>();
        for (String term : query) {
            tfByTerm.put(term, 0);
        }
        while (tokenizer.hasMoreElements()) {
            String term = normalize(tokenizer.nextElement());
            if (query.contains(term)) {
                tfByTerm.put(term, tfByTerm.get(term) + 1);
            }
        }
        int sharedTerms = 0;
        for (Integer tf : tfByTerm.values()) {
            if (tf > 0) {
                sharedTerms++;
            }
        }
        double score = ((double) sharedTerms) / query.size();
        boolean retrieve = (score > 0.5);
        return new FilterDecision(tweet.id, score, retrieve);
    }

    private String normalize(String term) {
        stemmer.setCurrent(term.toLowerCase());
        stemmer.stem();
        return stemmer.getCurrent();
    }

}
