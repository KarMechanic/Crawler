package org.crawler;

public class Main {
    public static void main(String[] args) {
        Crawler crawler = new Crawler();
        crawler.startCrawling("https://en.wikipedia.org/wiki/Open-source_intelligence", 2, 600);
//        crawler.startCrawling("https://en.wikipedia.org/wiki/Bread", 1, 60);
//        crawler.startCrawling("https://en.wikipedia.org/wiki/France", 2, 600);

    }
}