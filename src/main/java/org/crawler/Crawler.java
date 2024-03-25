package org.crawler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler {

    private final ConcurrentHashMap<String, Boolean> visitedLinks = new ConcurrentHashMap<>();
    private final Queue<Node> nodeQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> currentLinksToCrawl = new ConcurrentLinkedQueue<>();
    private final Queue<String> futureLinksToCrawl = new ConcurrentLinkedQueue<>();
    private final AtomicInteger currentDepth = new AtomicInteger();
    private ThreadPoolExecutor executorService;
    private ScheduledExecutorService scheduledExecutorService;

    private int maxDepth;
    private long crawlTimeLimit;

    public void startCrawling(String startUrl, int maxDepth, long crawlTimeLimitInSeconds) {
        initializeParameters(startUrl, maxDepth, crawlTimeLimitInSeconds);

        // add the first url for the first pass
        currentLinksToCrawl.add(startUrl);
        visitedLinks.put(startUrl, false);

        // schedule the shutdown tasks with the given crawl time limit
        scheduledExecutorService.schedule(this::shutdown, crawlTimeLimit, TimeUnit.SECONDS);

        crawlCurrentDepth();

        scheduledExecutorService.shutdownNow();
    }

    private void initializeParameters(String startUrl, int maxDepth, long crawlTimeLimitInSeconds) {
        this.maxDepth = maxDepth;
        this.crawlTimeLimit = crawlTimeLimitInSeconds;
        currentLinksToCrawl.clear();
        futureLinksToCrawl.clear();
        nodeQueue.clear();
        visitedLinks.clear();
        currentDepth.set(0);

        this.scheduledExecutorService = Executors.newScheduledThreadPool(5);
        int corePoolSize = 10;
        int maximumPoolSize = 100;
        long keepAliveTime = 5L;
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

        this.executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    private void crawlCurrentDepth() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        ExecutorCompletionService<Node> completionService = new ExecutorCompletionService<>(executorService);
        List<Future<Node>> futures = new ArrayList<>();

        while (!currentLinksToCrawl.isEmpty()) {
            String currentUrl = currentLinksToCrawl.poll();
            if (currentUrl == null) continue;

            CrawlerThread crawlerTask = new CrawlerThread(currentUrl, visitedLinks, futureLinksToCrawl);
            try {
                futures.add(completionService.submit(crawlerTask));
            } catch (RejectedExecutionException e) {
                return;
            }
        }

        // Collect results
        for (Future<Node> future : futures) {
            try {
                Node resultNode = future.get(); // Retrieves the result, blocking if not available
                if (resultNode != null) {
                    resultNode.setDepth(currentDepth.get());
                    nodeQueue.add(resultNode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Handle the interruption properly
                System.out.println(e.getMessage());
                break; // Exit the loop or handle accordingly
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof InterruptedException) {
                    break;
                }
                if (cause instanceof IOException) {
                    if (!executorService.isShutdown()) {
                        //TODO This would be where the functionality for rescheduling tasks would be.
                        // more explanation in the readme
                    }
                }
            }
        }
        swapQueuesAndCrawlNextDepth();  // Proceed to the next depth.
    }

    private void swapQueuesAndCrawlNextDepth() {
        currentLinksToCrawl.addAll(futureLinksToCrawl);
        futureLinksToCrawl.clear();
        currentDepth.incrementAndGet();

        if (currentDepth.get() >= maxDepth) {
            shutdown();
            return;
        }
        // Check if there are more links to crawl at the next depth
        if (!currentLinksToCrawl.isEmpty()) {
            crawlCurrentDepth();
        }
    }


    private void shutdown(){
        if (executorService.isShutdown() && scheduledExecutorService.isShutdown()) return;
        System.out.println("Shutting Down");
            try {
                executorService.shutdown();

                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }

                scheduledExecutorService.shutdown();

                System.out.println("Shutdown complete.");

            } catch (InterruptedException ie) {
                executorService.shutdownNow();
                scheduledExecutorService.shutdownNow();

                System.out.println("Shutdown interrupted.");
            }
        }

}

