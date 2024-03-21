package org.crawler;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);
    private final int MAX_DEPTH;
    private final int MAX_RETRIES = 3;
    private final int RETRY_DELAY = 2;

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
                    crawl(currentUrl, 0);
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

        if (currentDepth.get() >= MAX_DEPTH) {
            System.out.println("Shutting down at depth: " + currentDepth.get());
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

    private void crawl(String currentUrl, int attempt) {
        if (currentDepth.get() >= MAX_DEPTH) {
            // make an executor shutdown function that waits for all tasks to finish and then shuts down
            // since we reached the max depth required.
            System.out.println("Do we get here?");
        }

        if (!visitedLinks.replace(currentUrl, false, true)) return;

        try {
            Document document = Jsoup.connect(currentUrl).get();
            Elements links = document.select("a[href]");
            for (Element link : links) {
                String absHref = link.attr("abs:href");
                boolean shouldVisit = false;
                // this block is necessary to ensure that only one thread stuff here
                synchronized (this) {
                    shouldVisit = visitedLinks.putIfAbsent(absHref, false) == null;
                }
                if (shouldVisit) {
                    futureLinksToCrawl.add(absHref);
                    System.out.println(currentDepth + " " + absHref + " " + Thread.currentThread().threadId());
                }
            }
        } catch (IOException e) {
            if (attempt < MAX_RETRIES) {
                int delay = (int) Math.pow(2, attempt) * RETRY_DELAY;
                executorService.schedule(() -> crawl(currentUrl, attempt + 1), delay, TimeUnit.SECONDS);
            } else {
                System.out.println("Failed to crawl " + currentUrl + " after " + MAX_RETRIES + " attempts.");
            }
        }
    }
}
