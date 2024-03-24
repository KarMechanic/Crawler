package org.crawler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private final ThreadPoolExecutor executorService;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);

    private final Set<String> STOPWORDS = Set.of(
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours",
            "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers",
            "herself", "it", "its", "itself", "they", "them", "their", "theirs", "themselves",
            "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are",
            "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does",
            "did", "doing", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until",
            "while", "of", "at", "by", "for", "with", "about", "against", "between", "into",
            "through", "during", "before", "after", "above", "below", "to", "from", "up", "down",
            "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so",
            "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now"
    );


    private final int MAX_DEPTH;
    private final int MAX_RETRIES = 3;
    private final int RETRY_DELAY = 2;

    public Crawler(int maxDepth) {
        this.MAX_DEPTH = maxDepth;

        int corePoolSize = 10; // Minimum threads to keep alive
        int maximumPoolSize = 100; // Maximum threads to allow in the pool
        long keepAliveTime = 60L; // Keep alive time for idle threads
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(); // Work queue for tasks

        this.executorService = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                new ThreadPoolExecutor.CallerRunsPolicy() // This policy provides a simple feedback control mechanism
        );
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
                scheduledExecutorService.schedule(() ->
                        crawl(currentUrl, attempt + 1), delay, TimeUnit.SECONDS);
            } else {
                System.out.println("Failed to crawl " + currentUrl + " after " + MAX_RETRIES + " attempts.");
            }
        }
    }

    private List<String> processDocument(Document document) {
        // Extract text content from the document
        String text = document.text();

        // Remove punctuation, convert to lowercase, and split into words
        String[] words = text.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");

        // Filter out stopwords
        List<String> filteredWords = Arrays.stream(words)
                .filter(word -> !STOPWORDS.contains(word))
                .toList();

        // Example usage: Print out the filtered words. You can modify this part to suit your needs,
        // such as counting word frequencies or further analyzing the text.
        filteredWords.forEach(System.out::println);
        return filteredWords;
    }



}
