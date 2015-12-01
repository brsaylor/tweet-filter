#!/usr/bin/env python3

import sys

"""
From the given qrels file, find the oldest known relevant tweet for each of
the topics and print it out. This output was used to populate
querytweettime for each of the topics in
src/main/resources/2012.topics.MB1-50.filtering.training.[pruned].txt.
"""

oldestByTopic = {}

with open(sys.argv[1]) as f:
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
