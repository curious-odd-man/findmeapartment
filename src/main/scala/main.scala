import java.nio.file.{Files, Paths, StandardOpenOption}

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConversions._
import scala.io.Source

object main {

  private val FILE_TO_STORE_VIEWED_ADS = "AlreadyViewedAds.log"
  private val FILE_TO_STORE_LINKS_TO_ADS = "AlreadyViewedAdsLinks.log"
  private val OUTPUT_HTML_FILE = "NewAds.html"

  def toInt(s: String): Int = {
    try {
      s.toInt
    } catch {
      case e: Exception => 0
    }
  }

  case class Address(city: String, district: String, street: String)

  case class Advertisement(text: String, rooms: Integer, area: Float, seria: String, floor: String, price: Float, pricePerMeter: Float, homeType: String, originalLink: String, address: Address) {
    def roomsBetween(min: Int, max: Int): Boolean = {
      min <= rooms && rooms <= max
    }

    override def hashCode(): Int = text.hashCode()
  }

  def extractLinks(doc: Document): List[String] = {
    // Find main table element
    val mainTable = doc.getElementById("main_mtbl")
    // Get all rows in a table
    val linkInRow = mainTable.getElementsByTag("a")
    // Extract links to ads
    val links = linkInRow.eachAttr("href").toList.map("https://m.ss.com" + _)

    links
  }

  private def getAllPages(link: String) = {

    print("Acquiring ads pages: ")
    print(".")

    val firstPage = Jsoup.connect(link).get()
    val linkToLastPage = firstPage.getElementsByClass("navi navi_page_disabled")(0)
    val lastPageLinkeString = linkToLastPage.attr("href")
    val indexOfNumberStart = lastPageLinkeString.indexOf("/page")
    val lastPageIndex = lastPageLinkeString.substring(indexOfNumberStart + 5, lastPageLinkeString.length - ".html".length).toInt

    @scala.annotation.tailrec
    def mainloop(index: Int, docs: List[Document]): List[Document] = {
      val link = "https://m.ss.com/ru/real-estate/flats/riga/all/sell/page" + index + ".html"
      Thread.sleep((Math.random() * 1000).toLong) // avoid spamming ss.com with requests
      print(".")
      if (index > lastPageIndex) {
        println(" DONE!")
        docs
      }
      else {
        mainloop(index + 1, Jsoup.connect(link).get() :: docs)
      }
    }

    mainloop(2, List(firstPage))
  }

  private def numToArea(num: String): Float = {
    num.replaceAll(" м²", "").toFloat
  }

  private def linkToAdvertisement(link: String): Advertisement = {
    print(".")
    val doc = Jsoup.connect(link).get()
    Thread.sleep((Math.random() * 1000).toLong) // avoid spamming ss.com with requests
    val main = doc.getElementById("main")
    val table = main.getElementsByTag("tr").toList

    def tableRowToPair(e: Element): (String, String) = {
      val texts = e.getElementsByTag("td").toList.map(_.text)
      val value = {
        if (texts.size > 1) texts(1)
        else "NOVALUE"
      }
      (texts.head, value)
    }

    val map = table.map(tableRowToPair).toMap

    val prices = map("Цена:").replace(" ", "").replace("(", "").split("€")

    val addr = Address(map("Город:"), map("Район:"), map("Улица:"))
    val ad = Advertisement(main.text(), toInt(map("Комнат:")), numToArea(map("Площадь:")), map("Серия:"), map("Этаж / этажей:"), prices(0).toFloat, 0, map("Тип дома:"), link, addr)
    println(ad)
    ad
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

    val path1 = Paths.get(FILE_TO_STORE_LINKS_TO_ADS)

    if (!Files.exists(path1))
      Files.createFile(path1)

    val viewedAdsSource = Source.fromFile(FILE_TO_STORE_VIEWED_ADS)
    val linksOfViewedAdsSource = Source.fromFile(FILE_TO_STORE_LINKS_TO_ADS)

    val viewedAds = viewedAdsSource.getLines().map(_.toInt).toSet
    viewedAdsSource.close()
    val linksOfViewedAds = linksOfViewedAdsSource.getLines().toSet
    linksOfViewedAdsSource.close()

    println("Already seen " + viewedAds.size + " ads.")

    val allPages = getAllPages("https://m.ss.com/ru/real-estate/flats/riga/all/sell/")

    println("Found " + allPages.size + " new pages.")

    print("Extracting ads information: ")
    val allNewAds = allPages.par.flatMap(extractLinks)
      .filterNot(lnk => linksOfViewedAds.contains(lnk))
      .map(linkToAdvertisement)
      .filter(_.roomsBetween(minRooms, maxRooms))
      .filterNot(adv => viewedAds.contains(adv.hashCode()))
      .filter(ad => Seq("центр", "дарзциемс", "кенгарагс", "плявниеки", "пурвциемс", "катлакалнс", "зиепниеккалнс", "торнякалнс").contains(ad.address.district.toLowerCase))

    println("DONE!")

    println("Found " + allNewAds.size + " new ads.")

    val outputPath = Paths.get(OUTPUT_HTML_FILE)
    Files.deleteIfExists(outputPath)

    if (allNewAds.nonEmpty) {
      val generatedPage = allNewAds
        .map(_.originalLink.replaceFirst("https://m.ss.com", "https://ss.com"))
        .map(link => "<a href=\"" + link + "\">" + link + "</a>\n" +
          "<iframe src=\"" + link + "\" height=\"2000\" width=\"1850\" allowTransparency=\"true\" scrolling=\"yes\" ></iframe><br>\n")
        .reduce(_ + _)

      Files.write(outputPath, generatedPage.getBytes)
      Files.write(path, allNewAds.map(_.hashCode + "\n").reduce(_ + _).getBytes, StandardOpenOption.APPEND)
    }
  }
}
