package org.crawler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Node {
    private String url;
    private Map<String, Integer> wordFrequencies = new HashMap<>();

    // private final int depth;

    public Node(String url) {
        this.url =url;
    }

}
