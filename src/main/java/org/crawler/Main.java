package org.crawler;

public class Main {
    public static void main(String[] args) {
        Crawler crawler = new Crawler(1);
        crawler.startCrawling("https://en.wikipedia.org/wiki/Open-source_intelligence");
    }
}