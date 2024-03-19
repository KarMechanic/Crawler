package org.crawler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Node {
    private String url;
    private Map<String, Integer> wordFrequencies = new HashMap<>();
    private Set<String> childLinks = new HashSet<>();

    public Node(String url) {
        this.url = url;
    }

}
