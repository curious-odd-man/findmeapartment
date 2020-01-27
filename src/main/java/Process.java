import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Process {

    private static Path ensureExists(String p) throws IOException {
        return ensureExists(Paths.get(p));
    }

    private static Path ensureExists(Path p) throws IOException {
        if (!Files.exists(p)) {
            Files.createFile(p);
        }
        return p;
    }

    private static Path ensureDirExists(String p) throws IOException {
        return ensureDirExists(Paths.get(p));
    }

    private static Path ensureDirExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    public static void ensureFilesExist() throws IOException {
        ensureExists(Config.FILE_TO_STORE_VIEWED_ADS);
    }

    private static List<Document> getAllPages(String firstLink) throws IOException, InterruptedException {
        System.out.println("Acquiring ads pages: ");
        System.out.print(".");

        Document firstPage = Jsoup.connect(firstLink)
                                  .get();
        List<Document> res = new LinkedList<>();
        res.add(firstPage);

        Element linkToLastPage = firstPage.getElementsByClass("navi navi_page_disabled")
                                          .first();

        String lastPageLinkeString = linkToLastPage.attr("href");
        int indexOfNumberStart = lastPageLinkeString.indexOf("/page");
        int lastPageIndex = Integer.parseInt(lastPageLinkeString.substring(indexOfNumberStart + 5, lastPageLinkeString.length() - ".html".length()));

        for (int i = 2; i < lastPageIndex; ++i) {
            System.out.print(".");
            String link = Config.FIRST_PAGE_LINK + "page" + i + ".html";
            res.add(Jsoup.connect(link)
                         .get());
            Thread.sleep((long) (Math.random() * 1000)); // avoid spamming ss.com with requests
        }

        return res;
    }

    private static List<String> extractLinks(Document doc) {
        // Find main table element
        Element mainTable = doc.getElementById("main_mtbl");
        // Get all rows in a table
        Elements linkInRow = mainTable.getElementsByTag("a");
        // Extract links to ads
        return linkInRow.eachAttr("href")
                        .stream()
                        .map(v -> "https://m.ss.com" + v)
                        .collect(Collectors.toList());
    }

    private static void createAddsFiles(Collection<String> allAdsLinks) throws IOException, InterruptedException {
        Path addsFolder = ensureDirExists(Config.ADDS_FOLDER);

        for (String link : allAdsLinks) {
            String fname = Config.fileNameFromLink(link);
            Path adFile = addsFolder.resolve(fname);
            if (!Files.exists(adFile)) {
                Document document = Jsoup.connect(link)
                                         .get();

                Files.write(adFile, Collections.singleton(document.toString()));
                Thread.sleep((long) (Math.random() * 1000));  // avoid spamming
            }
        }
    }

    public static void process() throws IOException, InterruptedException {
        long timeFromLastRun;
        if (Files.exists(Config.LAST_RUN_FILE)) {
            String lastRun = Files.readAllLines(Config.LAST_RUN_FILE)
                                  .get(0);
            long now = LocalDateTime.now()
                                    .toEpochSecond(ZoneOffset.UTC);
            timeFromLastRun = now - Long.parseLong(lastRun);
        } else {
            timeFromLastRun = -1;
        }

        List<String> allAdsLinks = new LinkedList<>();

        if (timeFromLastRun < 0 || timeFromLastRun > Config.TIMEOUT_FETCH_PAGES.getSeconds()) {
            List<Document> allPages = getAllPages(Config.FIRST_PAGE_LINK);

            Files.write(Config.LAST_RUN_FILE, String.valueOf(LocalDateTime.now()
                                                                          .toEpochSecond(ZoneOffset.UTC))
                                                    .getBytes());

            System.out.println("Found " + allPages.size() + " new pages.");

            System.out.println("Extracting ads information: ");
            allAdsLinks = allPages.stream()
                                  .flatMap(v -> extractLinks(v).stream())
                                  .collect(Collectors.toList());

            Files.write(Config.ADDS_LIST_FILE, allAdsLinks);
        } else {
            System.err.println("Will not update pages since timeout for update is " + Config.TIMEOUT_FETCH_PAGES + ". From last run passed " + timeFromLastRun + " seconds");
            allAdsLinks = Files.readAllLines(Config.ADDS_LIST_FILE);
        }

        createAddsFiles(allAdsLinks);
    }
}
