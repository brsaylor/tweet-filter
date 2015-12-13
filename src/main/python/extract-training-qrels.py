#!/usr/bin/env python3

"""
From the complete file of 2011 Microblog Track relevance judgments included in
twitter-tools, extract the lines for the 2012 Microblog Track filtering task
training topics (1,6,11,...46) and print them to stdout. This produces
src/main/resources/filtering-qrels.training.
"""

with open('qrels.microblog2011.txt', 'r') as f:
    for line in f:
        if int(line.split()[0]) % 5 == 1:
            print(line, end='')
