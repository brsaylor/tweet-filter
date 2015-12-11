package com.bensaylor.tweetfilter;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.HashSet;

// Using org.tartarus.snowball directly instead of
// weka.core.stemmers.SnowballStemmer, which causes java.util.zip.ZipException
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.porterStemmer;

import weka.core.tokenizers.WordTokenizer;

/**
 * Simple query-based filter
 *
 * @author Ben Sayor
 */
public class QueryFilter extends Filter {

    private WordTokenizer tokenizer;
    private SnowballStemmer stemmer;
    private HashSet<String> query;
    private CharsetEncoder asciiEncoder;
    private String allowedNonAsciiChars;

    public QueryFilter() {
        tokenizer = new WordTokenizer();
        stemmer = new porterStemmer();
        asciiEncoder = Charset.forName("US-ASCII").newEncoder();

        allowedNonAsciiChars = new StringBuilder()
            .append("\u00a0") // non-breaking space
            .append("\u2018") // left single quote
            .append("\u2019") // right single quote
            .append("\u201C") // left double quote
            .append("\u201D") // right double quote
            .append("\u2026") // horizontal ellipsis
            .append("\u2013") // en dash
            .append("\u2014") // em dash
            .toString();
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

        // Try to exclude most non-English tweets by filtering out non-ASCII
        // First, preprocess to remove some non-ASCII chars that sometimes occur
        // in English
        String s = tweet.text.replaceAll("[" + allowedNonAsciiChars + "]", " ");
        if (!asciiEncoder.canEncode(s)) {
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
