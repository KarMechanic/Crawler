package org.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

public class Crawler {
    private HashSet<String> visitedLinks = new HashSet<>();
    private Queue<String> linksQueue = new LinkedList<>();


    private final int MAX_STEPS;

    public Crawler(int maxSteps) {
        this.MAX_STEPS = maxSteps;
    }

    public void crawl(String startUrl) {
        linksQueue.add(startUrl);
        int steps = 0;
        while (!linksQueue.isEmpty() && steps < MAX_STEPS) {
            String currentUrl = linksQueue.poll();
            if (currentUrl != null) {
                visitedLinks.add(currentUrl);
                try {
                    Document document = Jsoup.connect(currentUrl).get();
                    Elements links = document.select("a[href]");
                    for (Element link : links) {
                        String absHref = link.attr("abs:href");
                        if (visitedLinks.contains(absHref)) continue;
                        System.out.println(absHref);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            steps++;
        }
    }
}
