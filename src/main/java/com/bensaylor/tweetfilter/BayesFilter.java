package com.bensaylor.tweetfilter;

import java.util.ArrayList;

import weka.classifiers.bayes.NaiveBayesMultinomialText;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class BayesFilter extends Filter {

    private NaiveBayesMultinomialText classifier;
    private Instances instances;
    private int numRelevantExamples;
    private int numNonRelevantExamples;

    private double queryWeight = 4.0;
    //private double[] relevanceWeights = {2.0, 1.0, 2.0};

    @Override
    public void setTopic(Topic topic) {
        classifier = new NaiveBayesMultinomialText();

        classifier.setLowercaseTokens(true);

        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("text", (ArrayList<String>) null));
        ArrayList<String> labels = new ArrayList<>();
        labels.add("nonrelevant");
        labels.add("relevant");
        attributes.add(new Attribute("relevance", labels));

        instances = new Instances("tweets", attributes, 0);
        instances.setClassIndex(attributes.size() - 1);

        numRelevantExamples = 0;
        numNonRelevantExamples = 0;

        try {
            classifier.buildClassifier(instances);
        } catch (Exception e) {
            System.err.println("Error building classifier: " + e.getMessage());
        }

        Instance queryInstance = makeQueryInstance(topic.title);
        try {
            classifier.updateClassifier(queryInstance);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @Override
    public FilterDecision decide(Tweet tweet) {
        if (numRelevantExamples == 0 || numNonRelevantExamples == 0) {
            return new FilterDecision(tweet.id, 1.0, true);
        } else {
            // The classifier has been given at least one positive and one
            // negative example, so let it decide
            Instance instance = makeInstance(tweet, 0);
            double prediction;
            try {
                prediction = classifier.classifyInstance(instance);
            } catch (Exception e) {
                System.err.println("Error classifying instance");
                return null;
            }
            boolean retrieve = (prediction == 1);
            return new FilterDecision(tweet.id, 2.0, retrieve);
        }

        // score = 1.0 means it's being retrieved without classification
        // score = 2.0 means it went through the classifier
    }

    @Override
    public void feedback(Tweet tweet, int relevance) {
        try {
            Instance instance = makeInstance(tweet, relevance);
            classifier.updateClassifier(instance);
            if (relevance >= Constants.MINREL) {
                numNonRelevantExamples++;
            } else {
                numRelevantExamples++;
            }
        } catch (Exception e) {
            System.err.println("Error updating classifier: " + e.getMessage());
        }
    }

    private Instance makeInstance(Tweet tweet, int relevance) {
        double[] values = new double[instances.numAttributes()];
        //double weight = relevanceWeights[Math.max(0, relevance)];
        double weight;
        values[0] = instances.attribute(0).addStringValue(tweet.text);
        if (relevance >= Constants.MINREL) {
            values[1] = 1.0;
            // To prevent class skew, weight by the ratio of nonrelevant
            // instances to relevant instances
            if (numRelevantExamples + numNonRelevantExamples > 1)
                weight = numNonRelevantExamples / (double) numRelevantExamples;
            else
                weight = 1.0;
        } else {
            values[1] = 0.0;
            // To prevent class skew, weight by the ratio of relevant
            // instances to nonrelevant instances
            if (numRelevantExamples + numNonRelevantExamples > 1)
                weight = numRelevantExamples / (double) numNonRelevantExamples;
            else
                weight = 1.0;
        }

        // Prevent too-high or too-low weights
        if (weight < 0.25) weight = 0.25;
        if (weight > 4) weight = 4;

        Instance instance = new DenseInstance(weight, values);
        instance.setDataset(instances);
        return instance;
    }

    private Instance makeQueryInstance(String query) {
        double[] values = new double[instances.numAttributes()];
        values[0] = instances.attribute(0).addStringValue(query);
        values[1] = 1.0;
        Instance instance = new DenseInstance(queryWeight, values);
        instance.setDataset(instances);
        return instance;
    }
}
