package org.crawler;

import java.util.*;

/**
 * Represents a single node in the web crawling process, encapsulating the details of a crawled webpage.
 * A {@code Node} contains the webpage's URL, its depth in the crawl hierarchy, and a frequency map
 * of significant words found on the page.
 * <p>
 * The class provides methods to analyze text extracted from the webpage, storing word frequencies
 * in a map for later retrieval. It supports basic operations to get its properties and to compare
 * {@code Node} instances based on their URLs and depths.
 * </p>
 */
public class Node {
    private final String url;
    private int depth;
    private final Map<String, Integer> wordFrequencies = new HashMap<>();

    /**
     * Constructs a new {@code Node} instance associated with the specified URL.
     *
     * @param url The URL of the webpage this node represents.
     */
    public Node(String url) {
        this.url = url;
    }

    /**
     * Analyzes a list of processed words, incrementing their frequency counts in the word frequencies map.
     * This method is typically called after text extraction and processing from the webpage's content.
     *
     * @param processedWords A list of words extracted and processed from the webpage.
     */
    public void analyzeText(List<String> processedWords) {
        for (String word : processedWords) {
            wordFrequencies.put(word, wordFrequencies.getOrDefault(word, 0) + 1);
        }
    }

    /**
     * Returns the word frequencies map for this node, where keys are words and values are their
     * frequency counts on the webpage.
     *
     * @return A {@link Map} representing word frequencies.
     */
    public Map<String, Integer> getWordFrequencies() {
        return wordFrequencies;
    }

    /**
     * Returns the URL associated with this node.
     *
     * @return The URL as a {@link String}.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the depth of this node in the crawl hierarchy.
     *
     * @return The depth as an {@code int}.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the depth of this node in the crawl hierarchy.
     *
     * @param depth The depth to set.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * {@code Node} instances are considered equal if their URLs and depths are the same.
     *
     * @param o The reference object with which to compare.
     * @return {@code true} if this object is the same as the {@code o} argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(url, node.url);
    }

    /**
     * Returns a hash code value for the object, based on its URL and depth.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(url, depth);
    }
}
