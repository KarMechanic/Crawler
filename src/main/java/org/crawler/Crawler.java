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
    private final Queue<String> currentLinksToCrawl = new ConcurrentLinkedQueue<>();
    private final Queue<String> futureLinksToCrawl = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicInteger currentDepth = new AtomicInteger();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final int MAX_DEPTH;

    public Crawler(int maxDepth) {
        this.MAX_DEPTH = maxDepth;
    }


    public void startCrawling(String startUrl) {
        currentLinksToCrawl.add(startUrl);
        visitedLinks.put(startUrl, false);
        crawlCurrentDepth();
    }

    private void crawlCurrentDepth() {
        final CountDownLatch latch = new CountDownLatch(currentLinksToCrawl.size());

        while (!currentLinksToCrawl.isEmpty()) {
            String currentUrl = currentLinksToCrawl.poll();
            if (currentUrl == null) continue;
            activeTasks.incrementAndGet();
            executorService.submit(() -> {
                try {
                    crawl(currentUrl);
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
        currentDepth.incrementAndGet();

        if (currentDepth.get() > MAX_DEPTH) {
            executorService.shutdown();
            return;
        }
        // Check if there are more links to crawl at the next depth
        if (!currentLinksToCrawl.isEmpty()) {
            System.out.println("Entering Crawl");
            crawlCurrentDepth();
            return;
        }
        executorService.shutdown();
    }

    private void crawl(String currentUrl) {
        if (currentDepth.get() > MAX_DEPTH) {
            // make an executor shutdown function that waits for all tasks to finish and then shuts down
            // since we reached the max depth required.
        }

        if (!visitedLinks.replace(currentUrl, false, true)) return;
        System.out.println(currentDepth.get());

        try {
            Document document = Jsoup.connect(currentUrl).get();
            Elements links = document.select("a[href]");
            for (Element link : links) {
                String absHref = link.attr("abs:href");
                synchronized (this) {
                    if (visitedLinks.putIfAbsent(absHref, false) == null) {
                        futureLinksToCrawl.add(absHref);
                        System.out.println(currentDepth + " " + absHref + " " + Thread.currentThread().threadId());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
