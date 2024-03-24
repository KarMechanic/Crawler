package org.crawler;

import java.util.*;

public class Node {
    private final String url;
    private final int depth;
    private final Map<String, Integer> wordFrequencies = new HashMap<>();

    public Node(String url, int depth) {
        this.url = url;
        this.depth = depth;
    }

    public void analyzeText(List<String> processedWords) {
        for (String word : processedWords) {
            wordFrequencies.put(word, wordFrequencies.getOrDefault(word, 0) + 1);
        }
    }

    public Map<String, Integer> getWordFrequencies() {
        return wordFrequencies;
    }

    public String getUrl() {
        return url;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return depth == node.depth &&
                Objects.equals(url, node.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, depth);
    }
}
