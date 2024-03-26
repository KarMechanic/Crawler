package org.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code CrawlerThread} class represents a single crawling unit in a web crawler application,
 * responsible for downloading web pages, extracting links, and processing text content.
 * It implements {@code Callable<Node>} to enable concurrent crawling tasks and return a {@code Node}
 * representing the crawled page's data structure, including URL, depth, and word frequencies.
 * <p>
 * This class utilizes JSoup for HTML parsing and handles URL visitation tracking to avoid
 * redundant crawling of the same URLs. It also filters common English stop words from the
 * textual content of the web pages.
 * </p>
 */
public class CrawlerThread implements Callable<Node> {
    private final String url;

    private static ConcurrentHashMap<String, Boolean> visitedLinks;
    private static Queue<String> futureLinksToCrawl;

    //TODO This field is a shortcut taken, preferably this would be
    // switched to something such as reading it from a file
    // obtained from https://gist.github.com/sebleier/554280
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

    /**
     * Constructs a {@code CrawlerThread} with specified URL to crawl, a shared visited links tracking map,
     * and a shared queue for future URLs to crawl.
     *
     * @param currentUrl The URL that this crawler thread will start with.
     * @param visitedLinks A concurrent map tracking visited URLs to avoid re-crawling.
     * @param futureLinksToCrawl A queue for storing URLs discovered but not yet crawled.
     */
    public CrawlerThread(String currentUrl, ConcurrentHashMap<String, Boolean> visitedLinks, Queue<String> futureLinksToCrawl) {
        this.url = currentUrl;
        CrawlerThread.visitedLinks = visitedLinks;
        CrawlerThread.futureLinksToCrawl = futureLinksToCrawl;
    }

    /**
     * Initiates the crawling process for the assigned URL. This method is called when the thread
     * executing the {@code CrawlerThread} starts.
     *
     * @return A {@code Node} representing the crawled page, or {@code null} if the crawl was not successful.
     * @throws IOException if an IO error occurs during page download.
     * @throws InterruptedException if the thread is interrupted during execution.
     */
    @Override
    public Node call() throws IOException, InterruptedException {
        // check for interruption status
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }

        return crawl(url);
    }

    /**
     * Crawls the given URL, processes the document to extract links and text, and filters out common stop words.
     * This method updates the visited links and future links to crawl accordingly.
     *
     * @param currentUrl The URL to crawl.
     * @return A {@code Node} containing the URL, depth, and a frequency map of significant words found.
     * @throws IOException if an IO error occurs during page download.
     * @throws InterruptedException if the thread is interrupted during execution.
     */
    private Node crawl(String currentUrl) throws IOException, InterruptedException {
        // checks if we have already crawled this url
        if (!visitedLinks.replace(currentUrl, false, true)) return null;

        // we pass the interruptedException up the hierarchy
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

//        System.out.println("Crawling: " + currentUrl + "with thread: " + Thread.currentThread().threadId());

        // we use a node struct to keep track of the url, depth, and list of words with their respective frequencies
        Node currentNode = new Node(currentUrl);
        // JSoup is used to connect to the url and grab the hyperlinks on the page
        Document document = Jsoup.connect(currentUrl).get();
        List<String> processedWords = processDocument(document);
        currentNode.analyzeText(processedWords);
        Elements links = document.select("a[href]");

        for (Element link : links) {
            String absHref = link.attr("abs:href");
            boolean shouldVisit = false;
            // this block is necessary to ensure that each link is visited by a unique thread
            // avoids duplicates
            synchronized (this) {
                shouldVisit = visitedLinks.putIfAbsent(absHref, false) == null;
            }
            if (shouldVisit) {
                futureLinksToCrawl.add(absHref);
            }
        }

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        //TODO maybe comment this out for longer runs, its just here to show that the process works
        printMostPopularWord(currentNode);
        return currentNode;
    }

    /**
     * Prints the most frequently occurring word found on the webpage associated with the given {@code Node}.
     * This method sorts the entries of the word frequency map in descending order of frequency and prints
     * the word with the highest count along with its frequency.
     *
     * @param currentNode The {@code Node} representing the webpage whose most popular word is to be printed.
     *                    This node contains the URL of the webpage and a map of word frequencies.
     */
    private void printMostPopularWord(Node currentNode) {
        String currentUrl = currentNode.getUrl();
        Map<String, Integer> wordFrequencies = currentNode.getWordFrequencies();
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(wordFrequencies.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        if (!sortedEntries.isEmpty()) {
            Map.Entry<String, Integer> mostPopularEntry = sortedEntries.get(0);
            String mostPopularWord = mostPopularEntry.getKey();
            Integer frequency = mostPopularEntry.getValue();

            System.out.println("Most popular word at: " + currentUrl + " is [" + mostPopularWord + "] with a frequency of: " + frequency);
        } else {
            System.out.println("No words found at: " + currentUrl);
        }
    }

    /**
     * Processes the given {@code Document}, extracting text content, removing punctuation,
     * converting to lowercase, and filtering out common English stop words.
     *
     * @param document The JSoup {@code Document} to process.
     * @return A list of filtered, significant words from the document.
     */
    private List<String> processDocument(Document document) {
        // extract text from the document
        String text = document.text();

        // remove punctuation, convert to lowercase, and split into words
        String[] words = text.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");

        // filter out common stopwords
        List<String> filteredWords = Arrays.stream(words)
                .filter(word -> !STOPWORDS.contains(word))
                .toList();

        return filteredWords;
    }


}
