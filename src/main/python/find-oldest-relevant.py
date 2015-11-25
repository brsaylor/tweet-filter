#!/usr/bin/env python3

"""
From the training qrels file, find the oldest known relevant tweet for each of
the training topics and print it out. This output was used to populate
querytweettime for each of the topics in
src/main/resources/2012.topics.MB1-50.filtering.training.txt.
"""

oldestByTopic = {}

with open('filtering-qrels.training') as f:
    for line in f:
        tokens = line.split()
        topic = int(tokens[0])
        id = int(tokens[2])
        rel = int(tokens[3])
        if rel > 0:
            if id not in oldestByTopic or id < oldestByTopic[topic]:
                oldestByTopic[topic] = id

for topic, id in sorted(oldestByTopic.items()):
    print((topic, id))
