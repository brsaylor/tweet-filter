package com.bensaylor.tweetfilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import weka.core.tokenizers.WordTokenizer;

/**
 * Filter incorporating relevance feedback using the Rocchio algorithm
 *
 * @author Ben Sayor
 */
public class FeedbackFilter extends QueryFilter {


    private WordTokenizer tokenizer;
    private HashMap<String,Double> origQuery;
    private HashMap<String,Double> relDocSum, nonrelDocSum;
    private int relDocCount, nonrelDocCount;
    private HashMap<String,Double> expandedQuery;
    private double alpha, beta, gamma; // Rocchio parameters
    private double expandedTotalWeight; // Total weight of expanded query
    private double scoreThreshold;
    private HashSet<String> stopwords;

    public FeedbackFilter() {
        tokenizer = new WordTokenizer();
        stopwords = new HashSet<>();
        String[] stopwordsArray = {
            "the", "is", "at", "of", "on", "and", "a", "to"};
        stopwords.addAll(Arrays.asList(stopwordsArray));

        // Tuning parameters
        alpha = 1;
        beta = 0.75;
        gamma = 0.15;
        scoreThreshold = 0.5;
    }

    @Override
    public void setTopic(Topic topic) {

        // Create original query vector with all terms having weight 1
        origQuery = new HashMap<String,Double>();
        tokenizer.tokenize(topic.title);
        while (tokenizer.hasMoreElements()) {
            String term = normalize(tokenizer.nextElement());
            if (!isStopword(term))
                origQuery.put(term, 1.0);
        }

        relDocSum = new HashMap<String,Double>();
        nonrelDocSum = new HashMap<String,Double>();
        expandedQuery = new HashMap<String,Double>();
        relDocCount = 0;
        nonrelDocCount = 0;
        expandedTotalWeight = 0;

        // Initialize expanded query
        for (Map.Entry<String,Double> entry : origQuery.entrySet()) {
            expandedQuery.put(entry.getKey(), entry.getValue());
            expandedTotalWeight += entry.getValue();
        }
    }

    @Override
    public FilterDecision decide(Tweet tweet) {

        // Filter out simple retweets and non-English tweets
        if (tweet.text.startsWith("RT")) {
            return new FilterDecision(tweet.id, 0.0, false);
        }
        if (hasNonEnglishChars(tweet)) {
            return new FilterDecision(tweet.id, 0.0, false);
        }

        tokenizer.tokenize(preprocessText(tweet.text));

        double score = 0.0;
        while (tokenizer.hasMoreElements()) {
            String term = normalize(tokenizer.nextElement());
            if (!isStopword(term) && expandedQuery.containsKey(term)) {
                score += expandedQuery.get(term);
            }
        }
        score /= expandedTotalWeight;  // Normalize score
        boolean retrieve = (score > scoreThreshold);
        return new FilterDecision(tweet.id, score, retrieve);
    }

    @Override
    public void feedback(Tweet tweet, int relevance) {

        tokenizer.tokenize(preprocessText(tweet.text));

        if (relevance >= Constants.MINREL) {

            // Update relevant tweet vector
            while (tokenizer.hasMoreElements()) {
                String term = normalize(tokenizer.nextElement());
                if (!isStopword(term))
                    addTermToVector(relDocSum, term, 1);
            }
            relDocCount++;

        } else {

            // Update non-relevant tweet vector
            while (tokenizer.hasMoreElements()) {
                String term = normalize(tokenizer.nextElement());
                if (!isStopword(term))
                    addTermToVector(nonrelDocSum, term, 1);
            }
            nonrelDocCount++;
        }

        // Update expanded query
        expandedQuery = new HashMap<String,Double>();
        addToVector(expandedQuery, origQuery, alpha);

        if (relDocCount > 0)
            addToVector(expandedQuery, relDocSum, beta / relDocCount);
        if (nonrelDocCount > 0)
            addToVector(expandedQuery, nonrelDocSum, gamma / nonrelDocCount);

        // Clip negative weights
        for (Map.Entry<String,Double> entry : expandedQuery.entrySet()) {
            if (entry.getValue() < 0) {
                expandedQuery.put(entry.getKey(), 0.0);
            }
        }

        if (log != null) {
            logExpandedQuery();
        }

        // Calculate total weight for normalization
        expandedTotalWeight = 0;
        for (double w : expandedQuery.values()) {
            expandedTotalWeight += w;
        }
    }

    private void addToVector(
            HashMap<String,Double> vec,
            HashMap<String,Double> vecToAdd,
            double weight) {
        for (Map.Entry<String,Double> entry : vecToAdd.entrySet()) {
            addTermToVector(vec, entry.getKey(), weight);
        }
    }

    private void addTermToVector(HashMap<String,Double> vec, String term,
            double weight) {
        Double existingWeight = vec.get(term);
        if (existingWeight == null) {
            existingWeight = new Double(0);
        }
        vec.put(term, existingWeight + weight);
    }

    // Perform pre-tokenization processing
    private String preprocessText(String text) {
        String processed = text
            .replaceAll("http\\S+", "http") // Replace links with "http"
            .replaceAll("\\p{Punct}", " "); // Replace punctuation with space
        return processed;
    }

    private boolean isStopword(String token) {
        if (token.length() <= 1) {
            return true;
        }
        return stopwords.contains(token);
    }

    private void logExpandedQuery() {

        // Sort the vector entries by descending term weight, then term
        ArrayList<Map.Entry<String,Double>> entries
            = new ArrayList<Map.Entry<String,Double>>(expandedQuery.entrySet());
        Collections.sort(entries, new TermVectorComparator());
        
        log.println("Expanded query:");
        for (Map.Entry<String,Double> entry : entries) {
            log.printf("%12s\t%2.4f\n", entry.getKey(), entry.getValue());
        }
    }

    // Allows sorting term vectors by descending term weight, then term
    private class TermVectorComparator
            implements Comparator<Map.Entry<String,Double>> {
        
        public int compare(Map.Entry<String,Double> entry1,
                Map.Entry<String,Double> entry2) {

            if (entry1.getValue() > entry2.getValue()) {
                return -1;
            } else if (entry1.getValue() < entry2.getValue()) {
                return 1;
            } else {
                return entry1.getKey().compareTo(entry2.getKey());
            }
        }
    }
}
