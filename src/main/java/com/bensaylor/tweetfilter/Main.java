package com.bensaylor.tweetfilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

/**
 * tweet-filter main program.
 *
 * A version of the TREC 2012 Microblog track filtering task.
 *
 * The program is used by passing commmands and options as command-line
 * arguments. For a list of commands and options, see the help printed out when
 * the program is run without arguments.
 *
 * @author Ben Saylor
 */
public class Main {
    final private static File dbfile = new File("data/tweets.sqlite");
    final private static String trainingTopicsFile =
        "/2012.topics.MB1-50.filtering.training.pruned.txt";
    final private static String trainingQrelsFile =
        "/filtering-qrels.training.pruned";
    final private static String testTopicsFile =
        "/2012.topics.MB1-50.filtering.test.pruned.txt";
    final private static String testQrelsFile =
        "/filtering-qrels.test.pruned";

    private static TweetDatabase db = null;

    private static String topicsFile = trainingTopicsFile;
    private static String qrelsFile = trainingQrelsFile;
    private static String logFile = null;

    public static void main(String[] args) {

        // Get pre-command options
        int i;
        for (i = 0; i < args.length && args[i].startsWith("-"); i++) {
            String option = args[i];
            if (option.equals("-test")) {
                topicsFile = testTopicsFile;
                qrelsFile = testQrelsFile;
            } else if (option.equals("-log")) {
                i++;
                if (i >= args.length) {
                    printUsage();
                    return;
                }
                logFile = args[i];
            }
        }
        args = Arrays.copyOfRange(args, i, args.length);

        if (args.length > 0) {

            if (args[0].equals("createdb")) {
                if (args.length < 2) {
                    printUsage();
                } else {
                    createdb(args[1]);
                }

            } else if (args[0].equals("run")) {
                if (args.length < 4) {
                    printUsage();
                } else {
                    run(args[1], args[2], args[3]);
                }

            } else if (args[0].equals("stepfrom")) {
                if (args.length < 2) {
                    printUsage();
                } else {
                    stepfrom(args[1]);
                }

            } else if (args[0].equals("showtweet")) {
                if (args.length < 2) {
                    printUsage();
                } else {
                    showtweet(args[1]);
                }

            } else if (args[0].equals("showtopics")) {
                showtopics();

            } else if (args[0].equals("writeRelevantTweets")) {
                if (args.length < 2) {
                    printUsage();
                } else {
                    writeRelevantTweets(args[1]);
                }

            } else if (args[0].equals("writeqrels")) {
                if (args.length < 2) {
                    printUsage();
                } else {
                    writeqrels(args[1]);
                }

            } else if (args[0].equals("pruneqrels")) {
                if (args.length < 3) {
                    printUsage();
                } else {
                    pruneqrels(args[1], args[2]);
                }

            } else {
                printUsage();
            }
        } else {
            printUsage();
        }
    }

    /**
     * Print out the help text.
     */
    public static void printUsage() {
        System.err.println("\nUsage: tweet-filter [-test] [-log <logfile>] <command> [arguments]");
        System.err.println("  -test: use the test topics (otherwise, training topics will be used");
        System.err.println("  -log <logfile>: log filter messages to the given file");
        System.err.println();
        System.err.println("Commands:\n");

        System.err.println("createdb <input-list-file>\n"
                + "  Import the .json.gz files listed in <input-list-file>"
                + " into ./data/tweets.sqlite\n");

        System.err.println("run <filter> <run-tag> <output-file>\n"
                + "  Run the given filter with the training/test topics"
                + " and write the results to <output-file>"
                + " including the given <run-tag>.\n"
                + "  Available filters:\n"
                + "    baseline: classifies all tweets as relevant\n"
                + "    boolean-or: retrieves tweets with any of the terms in the query\n"
                + "    query: filter using query-based scoring only (no feedback)\n"
                + "    feedback: adds feedback to 'query' filter using Rocchio algorithm\n"
                + "    bayes: naive Bayes filter\n"
                );

        System.err.println("showtweet <tweet-id>\n"
                + "  Show the tweet with the given ID\n");

        System.err.println("stepfrom <start-tweet-id>\n"
                + "  Retrieve and display tweets in ID order,"
                + " starting from the given ID (no filtering)\n");

        System.err.println("showtopics\n"
                + "  Show the list of training/test topics\n");

        System.err.println("writeRelevantTweets <filename>\n"
                + "  Output the relevant tweets for each topic\n");

        System.err.println("writeqrels <filename>\n"
                + "  Write the training/test qrels to the given file"
                + " (mostly for testing that they were read correctly)\n");

        System.err.println("pruneqrels <infile> <outfile>\n"
                + "  Read qrels from <infile>, remove tweets not in DB,"
                + " write to <outfile>\n");
    }

    /**
     * Command: Create the tweet database and populate it from the listed files.
     *
     * @param inputListFile Filename containing names of .json.gz files
     */
    public static void createdb(String inputListFile) {
        ArrayList<String> filenameList = new ArrayList<>();
        try (
                BufferedReader reader = new BufferedReader(
                    new FileReader(inputListFile))) {
            System.out.println("I opened " + inputListFile + "!");
            String line;
            while ((line = reader.readLine()) != null) {
                filenameList.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading input list file: "
                    + e.getMessage());
            return;
        }
        String[] filenames = new String[0];
        filenames = filenameList.toArray(filenames);

        db = new TweetDatabase(dbfile);
        db.createTable();
        db.importJsonFiles(filenames);
    }

    /**
     * Command: Run the given filter on all training/test topics.
     *
     * @param filterName Name of the filter to run (see program usage message)
     * @param runTag String to include at end of each output line
     * @param outputFile Name of output file
     */
    public static void run(String filterName, String runTag, String outputFile) {
        Filter filter;
        if (filterName.equals("baseline")) {
            filter = new Filter();
        } else if (filterName.equals("boolean-or")) {
            filter = new BooleanOrFilter();
        } else if (filterName.equals("bayes")) {
            filter = new BayesFilter();
        } else if (filterName.equals("query")) {
            filter = new QueryFilter();
        } else if (filterName.equals("feedback")) {
            filter = new FeedbackFilter();
        } else {
            printUsage();
            return;
        }
        db = new TweetDatabase(dbfile);
        FilterController controller = new FilterController();
        controller.setDatabase(db);
        controller.setFilter(filter);
        controller.readTopics(controller.getClass().getResourceAsStream(
                    topicsFile));
        controller.readQrels(controller.getClass().getResourceAsStream(
                    qrelsFile));
        controller.run(runTag, outputFile, logFile);
    }

    /**
     * Command: Fetch and display the tweet with the given id.
     *
     * @param tweetIdString ID of the tweet to fetch
     */
    public static void showtweet(String tweetIdString) {
        long tweetId = Long.parseLong(tweetIdString);
        db = new TweetDatabase(dbfile);
        Tweet tweet = db.fetchTweet(tweetId);
        if (tweet != null) {
            System.out.println(tweet.toString());
        }
    }

    /**
     * Command: Interactively fetch and display tweets one-by-one in ID order.
     *
     * @param tweetIdString ID of tweet to start from
     */
    public static void stepfrom(String tweetIdString) {
        long tweetId = Long.parseLong(tweetIdString);
        db = new TweetDatabase(dbfile);
        db.startFromTweetId(tweetId);
        System.out.println("Press enter to retrieve each next tweet.");
        Scanner input = new Scanner(System.in);
        Tweet tweet;
        while ((tweet = db.next()) != null) {
            System.out.println(tweet.toString());
            input.nextLine();
        }
        input.close();
    }

    /**
     * Command: List the training topics.
     */
    public static void showtopics() {
        TopicsFileParser parser = new TopicsFileParser();
        InputStream inputStream = parser.getClass()
            .getResourceAsStream(topicsFile);
        ArrayList<Topic> topics = parser.parseTopics(inputStream);
        for (Topic topic : topics) {
            System.out.println(topic.toString());
        }
    }

    /**
     * Command: Output the list of relevant tweets.
     */
    public static void writeRelevantTweets(String filename) {
        db = new TweetDatabase(dbfile);
        FilterController controller = new FilterController();
        controller.setDatabase(db);
        controller.readTopics(controller.getClass().getResourceAsStream(
                    topicsFile));
        controller.readQrels(controller.getClass().getResourceAsStream(
                    qrelsFile));
        controller.writeRelevantTweets(filename);
    }

    /**
     * Command: Write the training qrels to the given file.
     *
     * @param filename The output qrels filename
     */
    public static void writeqrels(String filename) {
        FilterController controller = new FilterController();
        InputStream inputStream = controller.getClass()
            .getResourceAsStream(qrelsFile);
        controller.readQrels(inputStream);
        controller.writeQrels(filename);
    }

    /**
     * Command: prune qrels for which the tweet doesn't exist in the database.
     *
     * @param infile Input qrels file
     * @param outfile Output qrels file
     */
    public static void pruneqrels(String infile, String outfile) {
        try (
                BufferedReader reader = new BufferedReader(
                    new FileReader(infile));
                PrintWriter writer = new PrintWriter(outfile)) {
            
            db = new TweetDatabase(dbfile);

            String line;
            while ((line = reader.readLine()) != null) {
                long tweetId = Long.parseLong(line.split(" ")[2]);
                if (db.tweetExists(tweetId)) {
                    writer.println(line);
                }
            }

        } catch (FileNotFoundException e) {
            System.err.println("Error: file not found: " + infile);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
