package com.bensaylor.tweetfilter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * The FilterController performs the work common to all filters.
 * Topics, relevance judgments, and filters are interchangeable.
 * run() is the core method.
 *
 * @author Ben Saylor
 */
public class FilterController {

    // There are two ways of iterating over all of the tweets for a topic.
    // FETCHMODE_ALL iterates over all tweets in the database within the range
    // defined by the topic. This necessarily results in low precision as
    // calculated by the TREC evaluation scripts, which consider non-judged
    // tweets to be nonrelevant.
    // FETCHMODE_QRELS iterates over all of the judged tweets, avoiding this
    // problem. Note that the filter itself still does not have access to the
    // relevance judgment for a tweet until it has made a positive retrieval
    // decision for that tweet.
    private static final int FETCHMODE_ALL = 0;
    private static final int FETCHMODE_QRELS = 1;
    private int fetchMode = FETCHMODE_QRELS;

    private ArrayList<Topic> topics = null;
    private TweetDatabase db = null;
    private Filter filter = null;

    // Maps topic number to set of relevance judgments,
    // where each set of relevance judgments is a map from tweet ID to relevance
    // value. The relevance values are defined in Constants.
    private TreeMap<Integer, TreeMap<Long, Integer>> judgments = null;

    public FilterController() {
    }

    /**
     * @param db the TweetDatabase to set
     */
    public void setDatabase(TweetDatabase db) {
        this.db = db;
    }

    /**
     * @param filter the Filter to set
     */
    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    /**
     * Read the topics from a topics XML file given as an InputStream.
     * This allows reading the topics from a resource returned by
     * Class.getResourceAsStream(), or from a file on the filesystem.
     *
     * @param inputStream An InputStream for reading the topics file
     */
    public void readTopics(InputStream inputStream) {
        TopicsFileParser parser = new TopicsFileParser();
        topics = parser.parseTopics(inputStream);
    }

    /**
     * Read the relevance judgments from a qrels file given as an InputStream.
     * This allows reading the qrels from a resource returned by
     * Class.getResourceAsStream(), or from a file on the filesystem.
     *
     * @param inputStream An InputStream for reading the qrels file
     */
    public void readQrels(InputStream inputStream) {
        judgments = new TreeMap<>();
        BufferedReader reader
            = new BufferedReader(new InputStreamReader(inputStream));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ");
                Integer topicNumber = new Integer(tokens[0]);
                Long tweetId = new Long(tokens[2]);
                Integer relevance = new Integer(tokens[3]);
                if (!judgments.containsKey(topicNumber)) {
                    judgments.put(topicNumber, new TreeMap<Long, Integer>());
                }
                judgments.get(topicNumber).put(tweetId, relevance);
            }
        } catch (IOException e) {
            System.err.println("Error reading qrels file: "
                    + e.getMessage());
            judgments = null;
            return;
        }
    }

    /**
     * Run the current filter and save the output to outputFile.
     * This method implements the main loop of the program:
     * For each topic:
     *   For each tweet from [queryTweetTime to queryNewestTweet]:
     *     Use the current filter to assign a score and retrieval decision.
     *     Write the result to the output file.
     *     If the retrieval decision was positive:
     *       Reveal the relevance judgment to the filter.
     *
     * The output file has one line per topic per tweet with the following
     * space-separated fields:
     *   topic-number tweet-id score retrieval-decision run-tag
     * Example:
     *   MB01 3857291841983981 1.999 yes myRun
     *   MB01 3857291841983302 3.878 yes myRun
     *   ...
     *   MB02 3857291214283390 2.102 yes myRun
     *   ... 
     * 
     * See https://sites.google.com/site/microblogtrack/2012-guidelines for more
     * information. In this implementation, a decision is only written to the
     * file if the retrieval decision is positive.
     *
     * Before calling this method, readTopics(), readJudgments(), setDatabase(),
     * and setFilter() must be called.
     *
     * @param runTag A label identifying this run, to be included in the output
     * @param outputFile Name of the output file
     */
    public void run(String runTag, String outputFile) {
        assert topics != null;
        assert judgments != null;
        assert db != null;
        assert filter != null;

        PrintWriter writer;
        try {
            writer = new PrintWriter(outputFile);
        } catch (FileNotFoundException e) {
            System.err.println("Error opening output file: " + e.getMessage());
            return;
        }

        for (Topic topic : topics) {
            System.out.println("Running topic " + topic.number);
            filter.setTopic(topic);

            Tweet tweet = null;
            Map<Long,Integer> topicJudgments = judgments.get(topic.number);
            Iterator<Map.Entry<Long,Integer>> judgmentIterator = null;
            Map.Entry<Long,Integer> judgment = null;

            // See explanation given with the fetch mode variable declarations
            switch (fetchMode) {
                case FETCHMODE_QRELS:
                    // Because topicJudgments is a TreeMap, the iterator returns
                    // judgments in ascending order of tweet ID
                    judgmentIterator = topicJudgments.entrySet().iterator();
                    // Advance to the first tweet for the topic
                    do {
                        judgment = judgmentIterator.next();
                    } while (judgment.getKey() < topic.queryTweetTime);
                    tweet = db.fetchTweet(judgment.getKey());
                    break;
                case FETCHMODE_ALL:
                default:
                    db.startFromTweetId(topic.queryTweetTime);
                    // Get the first tweet for the topic
                    tweet = db.next();
            }

            if (tweet == null || tweet.id != topic.queryTweetTime) {
                System.err.println("Warning: topic " + topic.number +
                        ": oldest known relevant tweet is not in database");
                // TODO: How to handle this?
            } else {
                // Provide the first relevant tweet to the filter
                filter.feedback(tweet, topicJudgments.get(tweet.id));
            }

            // Main filtering loop
            while (tweet != null && tweet.id <= topic.queryNewestTweet) {
                FilterDecision decision = filter.decide(tweet);
                if (decision.retrieve) {
                    writer.printf("MB%03d %d %.3f yes %s\n",
                            topic.number,
                            tweet.id,
                            decision.score,
                            runTag);
                    if (topicJudgments.containsKey(tweet.id)) {
                        int relevance = topicJudgments.get(tweet.id);
                        filter.feedback(tweet, relevance);
                    }
                }

                // Fetch the next tweet
                switch (fetchMode) {
                    case FETCHMODE_QRELS:
                        if (judgmentIterator.hasNext()) {
                            judgment = judgmentIterator.next();
                            tweet = db.fetchTweet(judgment.getKey());
                        } else {
                            tweet = null;
                        }
                        break;
                    case FETCHMODE_ALL:
                    default:
                        tweet = db.next();
                }
            }
        }

        writer.close();
    }

    /**
     * Write the relevance judgments to a qrels file with the given name.
     * This is mostly for testing that the input qrels file was read correctly.
     *
     * @param filename The output qrels filename
     */
    public void writeQrels(String filename) {
        try (PrintWriter writer = new PrintWriter(filename)) {

            // Loop over topic numbers in ascending order
            for (Map.Entry<Integer,TreeMap<Long,Integer>> topicEntry
                    : judgments.entrySet()) {

                Integer topicNumber = topicEntry.getKey();
                TreeMap<Long,Integer> topicMap = topicEntry.getValue();

                // Loop over tweet IDs in descending order
                for (Map.Entry<Long,Integer> tweetEntry
                        : topicMap.descendingMap().entrySet()) {

                    Long tweetId = tweetEntry.getKey();
                    Integer relevance = tweetEntry.getValue();

                    writer.printf("%d 0 %d %d\n",
                            topicNumber, tweetId, relevance);
                }
            }
        } catch (Exception e) {
            System.err.println("Error writing qrels file: " + e.getMessage());
        }
    }
}
