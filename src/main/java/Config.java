import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public enum Config {

    ;

    public static final int    MIN_ROOMS       = 3;
    public static final int    MAX_ROOMS       = 3;
    public static final String WEB_PAGE        = "https://m.ss.com/";
    public static final String FIRST_PAGE_LINK = WEB_PAGE + "ru/real-estate/flats/riga/all/sell/";

    public static final Duration TIMEOUT_FETCH_PAGES = Duration.ofDays(1);

    public static final Path FILE_TO_STORE_VIEWED_ADS   = Paths.get("AlreadyViewedAds.log");
    public static final Path FILE_TO_STORE_LINKS_TO_ADS = Paths.get("AlreadyViewedAdsLinks.log");
    public static final Path OUTPUT_HTML_FILE           = Paths.get("NewAds.html");
    public static final Path LAST_RUN_FILE              = Paths.get("last-run.log");

    public static final Path ADDS_LIST_FILE = Paths.get("ads-list.log");
    public static final Path ADDS_FOLDER    = Paths.get("ads");


//    static {
//        System.setProperty("http.proxyHost", "proxy.lvrix.atrema.deloitte.com");
//        System.setProperty("http.proxyPort", "3128");
//        System.setProperty("https.proxyHost", "proxy.lvrix.atrema.deloitte.com");
//        System.setProperty("https.proxyPort", "3128");
//    }

    public static String fileNameFromLink(String in) {
        return in.replace(Config.WEB_PAGE, "")
                 .replaceAll("[\\/]", "_");
    }
}
