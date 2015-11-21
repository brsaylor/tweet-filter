package com.bensaylor.tweetfilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class Main {
    final private static File dbfile = new File("data/tweets.sqlite");
    private static TweetDatabase db = null;

    public static void main(String[] args) {
        if (args.length > 0) {
            if (args[0].equals("createdb")) {
                if (args.length < 2) {
                    printUsage();
                } else {
                    createdb(args[1]);
                }
            } else {
                printUsage();
            }
        } else {
            printUsage();
        }
    }

    public static void printUsage() {
        System.err.println("\nUsage: tweet-filter <command> [options]\n");
        System.err.println("Commands:\n");

        System.err.println("createdb <input-list-file>\n"
                + "  Import the .json.gz files listed in <input-list-file>"
                + " into ./data/tweets.sqlite\n");
    }

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
}
