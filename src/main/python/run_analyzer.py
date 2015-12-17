#!/usr/bin/env python3

"""
Based on a qrels file and a run file, identify tweets for each topic as true
positives, false positives, and false negatives, and write the results to an
output CSV file with one tweet per row.

True negatives are not included, because the vast majority of tweets are true
negatives.
"""

import sys
import sqlite3
import csv
import gzip

conn = None
cursor = None

def dict_factory(cursor, row):
    """
    Allows fetching rows from the database as dictionaries.
    From https://docs.python.org/3.5/library/sqlite3.html
    """
    d = {}
    for idx, col in enumerate(cursor.description):
        d[col[0]] = row[idx]
    return d

def fetchTweet(tweetId):
    cursor.execute('select * from tweets where id = ?', (tweetId,))
    return cursor.fetchone()

if __name__ == '__main__':
    if len(sys.argv) < 5:
        print("Usage: run_analyzer.py <db.sqlite> <qrels-file> <run-file> <output-file>")
        sys.exit(1)

    dbFilename = sys.argv[1]
    qrelsFilename = sys.argv[2]
    runFilename = sys.argv[3]
    outputFilename = sys.argv[4]

    conn = sqlite3.connect(dbFilename)
    conn.row_factory = dict_factory
    cursor = conn.cursor()

    # qrels and runData are dicts mapping topic numbers to sets of (judged or
    # predicted) relevant tweet IDs

    # Read qrels into memory
    qrels = {}
    qrelsFile = open(qrelsFilename, 'r')
    for line in qrelsFile:
        topicNumber, _, tweetId, relevance = line.split()
        if int(relevance) < 1:
            # Non-relevant and not-judged are the same
            continue
        topicNumber = int(topicNumber)
        if topicNumber not in qrels:
            qrels[topicNumber] = set()
        qrels[topicNumber].add(int(tweetId))
    qrelsFile.close()

    # Read runfile into memory
    runData = {}
    if runFilename.endswith('.gz'):
        runFile = gzip.open(runFilename, 'rt')
    else:
        runFile = open(runFilename, 'r')
    for line in runFile:
        tokens = line.split()
        if tokens[3] != 'yes':
            continue
        topicNumber = int(tokens[0][2:])
        tweetId = int(tokens[1])
        if topicNumber not in runData:
            runData[topicNumber] = set()
        runData[topicNumber].add(tweetId)
    runFile.close()

    outputFile = open(outputFilename, 'w')
    fieldnames = ['topic', 'tweetId', 'status', 'text', 'retweeted',
            'retweet_count', 'favorited', 'user_screen_name', 'user_name']
    writer = csv.DictWriter(outputFile, fieldnames)
    writer.writeheader()
    
    for topicNumber in sorted(qrels.keys()):
        truePositives = qrels[topicNumber] & runData[topicNumber]
        falsePositives = runData[topicNumber] - qrels[topicNumber]
        falseNegatives = qrels[topicNumber] - runData[topicNumber]

        for status, tweetIds in (
                ('TP', truePositives),
                ('FP', falsePositives),
                ('FN', falseNegatives)):
            for tweetId in sorted(tweetIds):
                tweet = fetchTweet(tweetId)
                writer.writerow({
                    'topic': topicNumber,
                    'tweetId': tweetId,
                    'status': status,
                    'text': tweet['text_'],
                    'retweeted': tweet['retweeted'],
                    'retweet_count': tweet['retweet_count'],
                    'favorited': tweet['favorited'],
                    'user_screen_name': tweet['user_screen_name'],
                    'user_name': tweet['user_name'],
                    })
    outputFile.close()
