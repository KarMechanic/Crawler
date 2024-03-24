package org.crawler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Node {
    private final String url;
    private final int depth;
    private Map<String, Integer> wordFrequencies = new HashMap<>();

    public Node(String url, int depth) {
        this.url = url;
        this.depth = depth;
    }

}
