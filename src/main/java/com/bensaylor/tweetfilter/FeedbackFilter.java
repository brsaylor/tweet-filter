package com.bensaylor.tweetfilter;

import java.util.HashMap;
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

    public FeedbackFilter() {
        tokenizer = new WordTokenizer();

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
            origQuery.put(normalize(tokenizer.nextElement()), 1.0);
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

        tokenizer.tokenize(tweet.text);

        double score = 0.0;
        while (tokenizer.hasMoreElements()) {
            String term = normalize(tokenizer.nextElement());
            if (expandedQuery.containsKey(term)) {
                score += expandedQuery.get(term);
            }
        }
        score /= expandedTotalWeight;  // Normalize score
        boolean retrieve = (score > scoreThreshold);
        return new FilterDecision(tweet.id, score, retrieve);
    }

    @Override
    public void feedback(Tweet tweet, int relevance) {

        tokenizer.tokenize(tweet.text);

        if (relevance >= Constants.MINREL) {

            // Update relevant tweet vector
            while (tokenizer.hasMoreElements()) {
                String term = normalize(tokenizer.nextElement());
                addTermToVector(relDocSum, term, 1);
            }
            relDocCount++;

        } else {

            // Update non-relevant tweet vector
            while (tokenizer.hasMoreElements()) {
                String term = normalize(tokenizer.nextElement());
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
}
