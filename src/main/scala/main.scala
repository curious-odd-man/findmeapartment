import java.nio.file.{Files, Paths, StandardOpenOption}

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.io.Source
import scala.collection.JavaConversions._

object main {

  private val FILE_TO_STORE_VIEWED_ADS = "AlreadyViewedAds.log"
  private val OUTPUT_HTML_FILE = "NewAds.html"

  case class Advertisement(text: String, rooms: Integer, area: Float, address: String, seria: String, floor: String, price: Float, pricePerMeter: Float, homeType: String, originalLink: String) {

    def roomsBetween(min: Int, max: Int): Boolean = {
      min <= rooms && rooms <= max
    }

    override def hashCode(): Int = text.hashCode()
  }

  def extractLinks(doc: Document): List[String] = {
    // Find main table element
    val mainTable = doc.getElementById("main_mtbl")
    // Get all rows in a table
    val rows = mainTable.getElementsByTag("tr")
    // Extract links to ads
    val links = rows.eachAttr("onclick").toList.map("https://m.ss.com" + _.split("'")(1))

    links
  }

  private def getAllPages(link: String) = {

    // Create map (link -> document)
    def mainloop(link: String, docs: Map[String, Document]): Map[String, Document] = {
      // Download page from link
      val doc = Jsoup.connect(link).get()
      // find footer with navigation buttons
      val navigationFooter = doc.getElementById("footer_navi")
      // get A HTML tags links from buttons
      val lastNextPageButtons = navigationFooter.getElementsByTag("a")
      // Create links
      val lastNextPageLinks = lastNextPageButtons.eachAttr("onclick").toList.map("https://m.ss.com" + _.split("'")(1))

      // On ss.com pressing next page on last page will get you to first age.
      // So we continue to getting pages unless we got page that was already processed
      if (docs.contains(link)) docs
      else {
        mainloop(lastNextPageLinks(1), docs + (link -> doc))
      }
    }

    mainloop(link, Map()).values
  }

  private def linkToAdvertisement(link: String): Advertisement = {

    val doc = Jsoup.connect(link).get()
    val main = doc.getElementById("main")
    val table = main.getElementsByTag("tr").toList

    def tableRowToPair(e: Element): (String, String) = {
      val texts = e.getElementsByTag("td").toList.map(_.text)
      (texts.head, texts(1))
    }

    val map = table.map(tableRowToPair).toMap

    val prices = map("Цена:").replace(" ", "").replace("(", "").split("€")

    Advertisement(main.text(), map("Комнат:").toInt, map("Площадь:").toFloat, map("Улица:"), map("Серия:"), map("Этаж / этажей:"), prices(0).toFloat, prices(1).toFloat, map("Тип дома:"), link)
  }

  def main(args: Array[String]): Unit = {

    val minRooms = {
      if (args.length >= 1) args(0).toInt
      else {
        println("Usage java -jar FindMeApartment minRooms [maxRooms]")
        System.exit(-1)
        0
      }
    }

    val maxRooms = {
      if (args.length >= 2) args(1).toInt
      else minRooms
    }

    val path = Paths.get(FILE_TO_STORE_VIEWED_ADS)

    if (!Files.exists(path))
      Files.createFile(path)

    val viewedAds = Source.fromFile(FILE_TO_STORE_VIEWED_ADS).getLines().map(_.toInt).toSet

    println("Already seen " + viewedAds.size + " ads.")

    val allPages = getAllPages("https://m.ss.com/ru/real-estate/flats/riga/kengarags/sell/")
    val allNewAds = allPages.flatMap(extractLinks).map(linkToAdvertisement).filter(_.roomsBetween(minRooms, maxRooms)).filterNot(adv => viewedAds.contains(adv.hashCode()))

    println("Found " + allNewAds.size + " new ads.")

    val outputPath = Paths.get(OUTPUT_HTML_FILE)
    Files.deleteIfExists(outputPath)

    if (allNewAds.nonEmpty) {
      val generatedPage = allNewAds.map("<iframe src=\"" + _.originalLink + "\" height=\"2000\" width=\"1850\" allowTransparency=\"true\" scrolling=\"no\" ></iframe><br>\n").reduce(_ + _)

      Files.write(outputPath, generatedPage.getBytes)
      Files.write(path, allNewAds.map(_.hashCode + "\n").reduce(_ + _).getBytes, StandardOpenOption.APPEND)
    }
  }
}
