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

    private final Set<String> visitedLinks = ConcurrentHashMap.newKeySet();
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
        activeTasks.incrementAndGet();
        executorService.submit(this::crawl);

        // Wait for initial tasks to finish
        while (activeTasks.get() > 0) {
            try {
                System.out.println(activeTasks.get());
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Done with initial tasks");
        executorService.shutdown();
        // Optionally wait for executorService to terminate and handle remaining tasks or shutdown actions
    }

    private void crawl() {
        while (!currentLinksToCrawl.isEmpty()) {
            CrawlURL currentPair = currentLinksToCrawl.poll();
            if (currentPair == null) {
                break;
            }
            String currentUrl = currentPair.url();
            int currentDepth = currentPair.depth();

            if (currentDepth >= MAX_DEPTH) {
                // should probably wait for all tasks here to finish and then shut down the service
                break;
            }

            visitedLinks.add(currentUrl);

            try {
                Document document = Jsoup.connect(currentUrl).get();
                Elements links = document.select("a[href]");
                for (Element link : links) {
                    String absHref = link.attr("abs:href");
                    if (visitedLinks.add(absHref)) {
                        if (currentDepth + 1 < MAX_DEPTH) {
                            futureLinksToCrawl.add(new CrawlURL(absHref, currentDepth + 1));
                            System.out.println(Thread.currentThread().getName() + " added " + absHref + " at depth " + (currentDepth + 1));
                            activeTasks.incrementAndGet();
                            executorService.submit(this::crawl);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
            }
        }
    }
}
