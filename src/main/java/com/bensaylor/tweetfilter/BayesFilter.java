package com.bensaylor.tweetfilter;

import java.util.ArrayList;

import weka.classifiers.bayes.NaiveBayesMultinomialText;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class BayesFilter extends Filter {

    NaiveBayesMultinomialText classifier;
    Instances instances;
    int numRelevantExamples;
    int numNonRelevantExamples;

    @Override
    public void setTopic(Topic topic) {
        classifier = new NaiveBayesMultinomialText();

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
        System.out.println("numRelevantExamples = " + numRelevantExamples);
        System.out.println("numNonRelevantExamples = " + numNonRelevantExamples);
    }

    public Instance makeInstance(Tweet tweet, int relevance) {
        double[] values = new double[instances.numAttributes()];
        values[0] = instances.attribute(0).addStringValue(tweet.text);
        if (relevance >= Constants.MINREL) {
            values[1] = 1.0;
        } else {
            values[1] = 0.0;
        }
        Instance instance = new DenseInstance(1.0, values);
        instance.setDataset(instances);
        return instance;
    }
}
