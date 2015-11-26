package com.bensaylor.tweetfilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * XML parser for TREC Microblog track 2012 filtering topics files.
 * Note: The TREC-provided XML files lack a document root element. In order for
 * the parser to work, the files must be edited to add <topics> at the top and
 * </topics> at the bottom.
 *
 * @author Ben Saylor
 */
public class TopicsFileParser {

    /**
     * Parse the given topics XML file and return a list of Topics.
     *
     * @param inputStream The topics XML file as an InputStream
     * @return The list of Topics in the file, or null in case of error
     */
    public ArrayList<Topic> parseTopics(InputStream inputStream) {
        ArrayList<Topic> topics = null;
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        try {
            SAXParser saxParser = saxParserFactory.newSAXParser();
            TopicsFileParserHandler handler = new TopicsFileParserHandler();
            saxParser.parse(inputStream, handler);
            topics = handler.getTopics();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        return topics;
    }
}

class TopicsFileParserHandler extends DefaultHandler {

    // Name of the current child element of the current <top> element (if any)
    private String currentChild = null;

    // Text content of the current child element
    private StringBuffer currentChildContent = null;

    // The current Topic being parsed
    private Topic currentTopic = null;

    // The list of topics parsed so far
    private ArrayList<Topic> topics = null;

    public ArrayList<Topic> getTopics() {
        return topics;
    }

    @Override
    public void startElement(String namespaceURI, String localName,
            String qName, Attributes attributes) throws SAXException {
        if (qName.equals("topics")) {
            // Root element; ignore
        } else if (qName.equals("top")) {
            currentTopic = new Topic();
        } else {
            currentChild = qName;
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName,
            String qName) throws SAXException {
        if (qName.equals("topics")) {
            // Root element; ignore
        } else if (qName.equals("top")) {
            if (topics == null) {
                topics = new ArrayList<Topic>();
            }
            topics.add(currentTopic);
            currentTopic = null;
        } else {
            // Set the value of the topic attribute that was just parsed
            String text = currentChildContent.toString().trim();
            if (currentChild.equals("num")) {
                currentTopic.number = Integer.parseInt(text.split("MB")[1]);
            } else if (currentChild.equals("title")) {
                currentTopic.title = text;
            } else if (currentChild.equals("querytime")) {
                currentTopic.queryTime = text;
            } else if (currentChild.equals("querytweettime")) {
                currentTopic.queryTweetTime = Long.parseLong(text);
            } else if (currentChild.equals("querynewesttweet")) {
                currentTopic.queryNewestTweet = Long.parseLong(text);
            }
            currentChild = null;
            currentChildContent = null;
        }
    }

    @Override
    public void characters(char[] ch, int start, int len) throws SAXException {
        if (currentChild == null) {
            // Ignore any characters not inside a child element of <top>
            return;
        }
        if (currentChildContent == null) {
            currentChildContent = new StringBuffer();
        }
        currentChildContent.append(ch, start, len);
    }
}
