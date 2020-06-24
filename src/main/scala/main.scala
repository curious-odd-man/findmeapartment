import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Objects

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConversions._
import scala.collection.immutable.HashSet
import scala.io.Source

object main {

  private val FILE_TO_STORE_KNOWN_ADS = "AllLinks.log"
  private val OUTPUT = "OUTPUT.log"
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

  case class Advertisement(address: Address, seria: String, homeType: String, text: String, rooms: Integer, area: Float, floor: String, price: Float, pricePerMeter: Float, originalLink: String, date: String) {
    def roomsBetween(min: Int, max: Int): Boolean = {
      min <= rooms && rooms <= max
    }

    override def hashCode(): Int = Objects.hash(address, rooms, seria, floor)
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

    val prices = map.getOrDefault("Цена:", "ERROR").replace(" ", "").replace("(", "").split("€")

    val addr = Address(map.getOrDefault("Город:", "ERROR"), map.getOrDefault("Район:", "ERROR"), map.getOrDefault("Улица:", "ERROR").replace("[Карта]", "").trim)
    val ad = Advertisement(addr, map.getOrDefault("Серия:", "ERROR"),
      map.getOrDefault("Тип дома:", "ERROR"), main.text(), toInt(map.getOrDefault("Комнат:", "0")),
      numToArea(map.getOrDefault("Площадь:", "0")), map.getOrDefault("Этаж / этажей:", "ERROR"), prices(0).toFloat, 0, link, map.getOrDefault("Дата:", "ERROR"))
    ad
  }

  def main(args: Array[String]): Unit = {
    val knownAds = Paths.get(FILE_TO_STORE_KNOWN_ADS)
    if (!Files.exists(knownAds))
      Files.createFile(knownAds)

    val outputPath = Paths.get(OUTPUT)
    if (!Files.exists(outputPath))
      Files.createFile(outputPath)

    val allPages = getAllPages("https://m.ss.com/ru/real-estate/flats/riga/all/sell/")
    println("Found " + allPages.size + " new pages.")

    val viewedAdsSource = Source.fromFile(FILE_TO_STORE_KNOWN_ADS)
    val viewedAds = HashSet() ++ viewedAdsSource.getLines()
    viewedAdsSource.close()

    allPages.toStream
      .flatMap(extractLinks)
      .map(linkToAdvertisement)
      .filter(ad => ad.address.district.toLowerCase.equals("кенгарагс"))
      .filter(ad => ad.roomsBetween(3, 4))
      .filter(ad => ad.seria == "Лит. пр.")
      .map(v => {
        println(v)
        v
      })
      .filter(ad => {
      val res = !viewedAds.contains(ad.toString)
      println("After filter: " + res)
      res
    })
      .map(ad => {
        Files.write(knownAds, (ad.toString + "\n").getBytes, StandardOpenOption.APPEND)
        ad
      })
      .map(ad => List(ad.address.street, ad.floor, ad.rooms, ad.price, ad.originalLink).mkString("\t"))
      .foreach(str => Files.write(outputPath, (str + "\n").getBytes, StandardOpenOption.APPEND))


    //    val linksOfViewedAdsSource = Source.fromFile(FILE_TO_STORE_LINKS_TO_ADS)
    //    val linksOfViewedAds = linksOfViewedAdsSource.getLines().toSet
    //    linksOfViewedAdsSource.close()

    //    print("Extracting ads information: ")
    //    val allNewAds = allPages.par.flatMap(extractLinks)
    //      .filterNot(lnk => linksOfViewedAds.contains(lnk))
    //      .map(linkToAdvertisement)
    //      .filter(_.roomsBetween(minRooms, maxRooms))
    //      .filterNot(adv => viewedAds.contains(adv.hashCode()))
    //      .filter(ad => Seq("центр", "дарзциемс", "кенгарагс", "плявниеки", "пурвциемс", "катлакалнс", "зиепниеккалнс", "торнякалнс").contains(ad.address.district.toLowerCase))
    //
    //    println("DONE!")
    //
    //    println("Found " + allNewAds.size + " new ads.")
    //
    //    val outputPath = Paths.get(OUTPUT_HTML_FILE)
    //    Files.deleteIfExists(outputPath)
    //
    //    if (allNewAds.nonEmpty) {
    //      val generatedPage = allNewAds
    //        .map(_.originalLink.replaceFirst("https://m.ss.com", "https://ss.com"))
    //        .map(link => "<a href=\"" + link + "\">" + link + "</a>\n" +
    //          "<iframe src=\"" + link + "\" height=\"2000\" width=\"1850\" allowTransparency=\"true\" scrolling=\"yes\" ></iframe><br>\n")
    //        .reduce(_ + _)
    //
    //      Files.write(outputPath, generatedPage.getBytes)
    //      Files.write(path, allNewAds.map(_.hashCode + "\n").reduce(_ + _).getBytes, StandardOpenOption.APPEND)
    //    }
  }
}
