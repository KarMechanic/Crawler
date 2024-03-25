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
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicInteger currentDepth = new AtomicInteger();
    private ThreadPoolExecutor executorService;
    private ScheduledExecutorService scheduledExecutorService;

    private int maxDepth;
    private long crawlTimeLimit;

    private Future<?> scheduledShutdownTask;

    public Crawler() {

    }

    public void startCrawling(String startUrl, int maxDepth, long crawlTimeLimitInSeconds) {
        initializeParameters(startUrl, maxDepth, crawlTimeLimitInSeconds);

        currentLinksToCrawl.add(startUrl);
        visitedLinks.put(startUrl, false);
        scheduledShutdownTask = scheduledExecutorService.schedule(this::shutdown, crawlTimeLimit, TimeUnit.SECONDS);
        crawlCurrentDepth();
        executorService.shutdownNow();
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
        activeTasks.set(0);

        this.scheduledExecutorService = Executors.newScheduledThreadPool(5);
        int corePoolSize = 10; // Minimum threads to keep alive
        int maximumPoolSize = 100; // Maximum threads to allow in the pool
        long keepAliveTime = 5L; // Keep alive time for idle threads
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(); // Work queue for tasks

        this.executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    private void crawlCurrentDepth() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        ExecutorCompletionService<Node> completionService = new ExecutorCompletionService<>(executorService);
        int tasksSubmitted = 0;

        while (!currentLinksToCrawl.isEmpty()) {
            String currentUrl = currentLinksToCrawl.poll();
            if (currentUrl == null) {
                continue;
            }
            CrawlerThread crawlerTask = new CrawlerThread(currentUrl, currentDepth.get(), visitedLinks, futureLinksToCrawl);
            completionService.submit(crawlerTask);
            tasksSubmitted++;
        }

        // Collect results
        for (int i = 0; i < tasksSubmitted; i++) {
            try {
                Future<Node> future = completionService.take(); // Blocks until any callable completes
                Node resultNode = future.get(); // Retrieves the result, blocking if not available
                if (resultNode != null) {
                    nodeQueue.add(resultNode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Handle the interruption properly
                break; // Exit the loop or handle accordingly
            } catch (ExecutionException e) {
                // Handle exceptions from the task execution
                // This is where you could implement retry logic if necessary
            }
        }
            swapQueuesAndCrawlNextDepth();  // Proceed to the next depth.
    }

    private void swapQueuesAndCrawlNextDepth() {
        currentLinksToCrawl.addAll(futureLinksToCrawl);
        futureLinksToCrawl.clear();
        currentDepth.incrementAndGet();

        if (currentDepth.get() >= maxDepth) {
//            shutdown(); placeholder
            return;
        }
        // Check if there are more links to crawl at the next depth
        if (!currentLinksToCrawl.isEmpty()) {
            System.out.println("Entering Crawl");
            crawlCurrentDepth();
        }
    }


    private void shutdown(){
        // placeholder
    }
}

