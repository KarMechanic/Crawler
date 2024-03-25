package org.crawler;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CrawlerThread implements Callable<Node> {
    private int depth;

    private String url;

    private int attempt;

    private static ConcurrentHashMap<String, Boolean> visitedLinks;
    private static Queue<String> futureLinksToCrawl;
    private final static Set<String> STOPWORDS = Set.of(
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

    private final int MAX_RETRIES = 3;
    private final long RETRY_DELAY = 2L;
    public CrawlerThread(String url) {

    }

    public CrawlerThread(String currentUrl, int depth, ConcurrentHashMap<String, Boolean> visitedLinks, Queue<String> futureLinksToCrawl) {
        this.url = currentUrl;
        this.depth = depth;
        CrawlerThread.visitedLinks = visitedLinks;
        CrawlerThread.futureLinksToCrawl = futureLinksToCrawl;
    }

    @Override
    public Node call() throws IOException {
        // Check for current thread interruption status
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }


        return crawl(url, attempt);
    }

    private Node crawl(String currentUrl, int attempt) throws IOException {
        if (!visitedLinks.replace(currentUrl, false, true)) return null;
        if (attempt >= MAX_RETRIES) return null;

        Node currentNode = new Node(currentUrl, depth);
        System.out.println("Crawling: " + currentUrl + " " + Thread.currentThread().threadId());
        Document document = Jsoup.connect(currentUrl).get();
        List<String> processedWords = processDocument(document);
        currentNode.analyzeText(processedWords);
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
//                    System.out.println(currentDepth + " " + absHref + " " + Thread.currentThread().threadId());
            }
        }
        return currentNode;
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

        return filteredWords;
    }


}
