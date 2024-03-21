package org.crawler;

import java.io.IOException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.crawler.CrawlURL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler {

    private final ConcurrentHashMap<String, Boolean> visitedLinks = new ConcurrentHashMap<>();
    private final Queue<CrawlURL> currentLinksToCrawl = new ConcurrentLinkedQueue<>();
    private final Queue<CrawlURL> futureLinksToCrawl = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final int MAX_DEPTH;

    public Crawler(int maxDepth) {
        this.MAX_DEPTH = maxDepth;
    }


    public void startCrawling(String startUrl) {
        currentLinksToCrawl.add(new CrawlURL(startUrl, 0));
        crawlCurrentDepth();
    }

    private void crawlCurrentDepth() {
        final CountDownLatch latch = new CountDownLatch(currentLinksToCrawl.size());

        while (!currentLinksToCrawl.isEmpty()) {
            CrawlURL currentPair = currentLinksToCrawl.poll();
            if (currentPair == null) continue;
            activeTasks.incrementAndGet();
            executorService.submit(() -> {
                try {
                    crawl(currentPair);
                } finally {
                    activeTasks.decrementAndGet();
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();  // Wait for all submitted tasks to finish.
            swapQueuesAndCrawlNextDepth();  // Proceed to the next depth.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    private void swapQueuesAndCrawlNextDepth() {
        currentLinksToCrawl.addAll(futureLinksToCrawl);
        futureLinksToCrawl.clear();
        // Check if there are more links to crawl at the next depth
        if (!currentLinksToCrawl.isEmpty()) {
            System.out.println("Entering Crawl");
            crawlCurrentDepth();
            return;
        }
        executorService.shutdown();
    }

    private void crawl(CrawlURL crawlURL) {
        String currentUrl = crawlURL.url();
        int currentDepth = crawlURL.depth();
        System.out.println(currentDepth);
//        for (String link : visitedLinks) {
//            System.out.println(currentDepth + " " + link);
//        }

//        boolean alreadyVisited = !visitedLinks.add(currentUrl);
        if (!visitedLinks.add(currentUrl)) return;


        if (currentDepth < MAX_DEPTH) {
            try {
                Document document = Jsoup.connect(currentUrl).get();
                Elements links = document.select("a[href]");
                for (Element link : links) {
                    String absHref = link.attr("abs:href");
                    if (visitedLinks.add(absHref)) {
                        System.out.println("here");
                        futureLinksToCrawl.add(new CrawlURL(absHref, currentDepth + 1));
                        System.out.println(currentDepth + " " + absHref + " " + Thread.currentThread().threadId());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
