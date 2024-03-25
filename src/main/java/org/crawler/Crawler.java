package org.crawler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code Crawler} class orchestrates a multi-threaded web crawler, managing the discovery and
 * processing of web pages in a concurrent manner. It utilizes a pool of {@code CrawlerThread} instances
 * to fetch and analyze pages, maintaining a queue of URLs to visit, a set of visited URLs to avoid
 * re-crawling, and a queue of nodes representing the structured information of each visited page.
 * <p>
 * Crawling is depth-based, with the ability to limit the maximum depth and total crawling time.
 * The class supports shutdown to terminate ongoing tasks and scheduled tasks preemptively.
 * </p>
 */
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

    /**
     * Starts the crawling process from a specified URL, with set maximum depth and time limits.
     * Initializes crawling parameters and manages the transition between different depths of
     * crawling, while scheduling a shutdown task to limit the overall execution time.
     *
     * @param startUrl The starting point URL for the web crawl.
     * @param maxDepth The maximum depth to crawl to.
     * @param crawlTimeLimitInSeconds The time limit for the crawl operation in seconds.
     */
    public void startCrawling(String startUrl, int maxDepth, long crawlTimeLimitInSeconds) {
        initializeParameters(startUrl, maxDepth, crawlTimeLimitInSeconds);

        // add the first url for the first pass
        currentLinksToCrawl.add(startUrl);
        visitedLinks.put(startUrl, false);

        // schedule the shutdown tasks with the given crawl time limit
        Future<?> scheduledShutdownTask = scheduledExecutorService.schedule(
                this::shutdown, crawlTimeLimit, TimeUnit.SECONDS);

        crawlCurrentDepth();
        System.out.println("Finished crawling at depth: " + currentDepth.get());

        // cancel the shutdown task if it hasn't run yet
        if (!scheduledShutdownTask.isDone()){
            scheduledShutdownTask.cancel(false);
        }
        scheduledExecutorService.shutdownNow();
    }

    /**
     * Initializes and resets the crawler's parameters, including URL queues, visited links tracking,
     * and executor services. This method prepares the crawler for a new crawling operation.
     *
     * @param startUrl The starting URL for the crawl.
     * @param maxDepth The maximum depth to crawl.
     * @param crawlTimeLimitInSeconds The maximum time allowed for the crawl in seconds.
     */
    private void initializeParameters(String startUrl, int maxDepth, long crawlTimeLimitInSeconds) {
        this.maxDepth = maxDepth;
        this.crawlTimeLimit = crawlTimeLimitInSeconds;
        currentLinksToCrawl.clear();
        futureLinksToCrawl.clear();
        nodeQueue.clear();
        visitedLinks.clear();
        currentDepth.set(0);

        //TODO values for the dynamic executor service are arbitrary, testing would have to be done to
        // determine which values correspond with the best results
        this.scheduledExecutorService = Executors.newScheduledThreadPool(5);
        int corePoolSize = 10;
        int maximumPoolSize = 100;
        long keepAliveTime = 5L;
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

        this.executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * Manages the crawling of the current depth. It submits {@code CrawlerThread} tasks for each URL
     * to be crawled at the current depth, collects the results, and prepares for crawling the next
     * depth level.
     */
    private void crawlCurrentDepth() {
        if (Thread.currentThread().isInterrupted()) return;

        ExecutorCompletionService<Node> completionService = new ExecutorCompletionService<>(executorService);
        List<Future<Node>> futures = new ArrayList<>();

        // go through all links that we should crawl through
        while (!currentLinksToCrawl.isEmpty()) {
            String currentUrl = currentLinksToCrawl.poll();
            if (currentUrl == null) continue;

            // each link is made into its own task which is then submitted to the executor service
            CrawlerThread crawlerTask = new CrawlerThread(currentUrl, visitedLinks, futureLinksToCrawl);
            try {
                futures.add(completionService.submit(crawlerTask));
            } catch (RejectedExecutionException e) {
                // if we got here then we most likely shutdown while crawling due to the time limit
                return;
            }
        }

       // collect all futures
        for (Future<Node> future : futures) {
            try {
                // retrieve and add the nodes we get from our threads
                Node resultNode = future.get();
                if (resultNode != null) {
                    resultNode.setDepth(currentDepth.get());
                    nodeQueue.add(resultNode);
                }

            } catch (InterruptedException e) {
                // if we got interrupted we want to leave this loop
                break;
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
        // go to the next depth
        swapQueuesAndCrawlNextDepth();
    }

    /**
     * Prepares for crawling the next depth level by transferring URLs from the future queue to the
     * current queue and incrementing the depth counter. If the maximum depth has been reached, or
     * there are no more URLs to crawl, it initiates shutdown.
     */
    private void swapQueuesAndCrawlNextDepth() {
        currentLinksToCrawl.addAll(futureLinksToCrawl);
        futureLinksToCrawl.clear();
        currentDepth.incrementAndGet();

        // if we've reached the desired depth we can end the program
        if (currentDepth.get() >= maxDepth) {
            shutdown();
            return;
        }

        // if there are more links to crawl at the next depth we continue
        if (!currentLinksToCrawl.isEmpty() && !executorService.isShutdown()) {
            System.out.println("Advancing to depth: " + currentDepth.get());
            crawlCurrentDepth();
        }
    }


    /**
     * Initiates a shutdown of the crawler, attempting to stop all ongoing tasks and
     * executor services. Ensures that the system is left in a consistent state post-shutdown.
     */
    private void shutdown(){
        if (executorService.isShutdown() && scheduledExecutorService.isShutdown()) return;
        System.out.println("Shutting Down");
            try {
                // Shut down the main service responsible for the task submission
                executorService.shutdown();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }

                // shut down the executor responsible for scheduled tasks
                scheduledExecutorService.shutdown();

                System.out.println("Shutdown complete.");
            } catch (InterruptedException ie) {
                executorService.shutdownNow();
                scheduledExecutorService.shutdownNow();

                System.out.println("Shutdown interrupted.");
            }
        }

}

