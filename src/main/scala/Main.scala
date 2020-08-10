import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Objects

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}

import scala.collection.JavaConversions._
import scala.collection.immutable.HashSet
import scala.io.Source

object Main {

  private val FILE_TO_STORE_KNOWN_ADS = "AllLinks_119.log"
  private val OUTPUT = "OUTPUT_119.log"
  private val FILE_TO_STORE_VIEWED_ADS = "AlreadyViewedAds.log"
  private val FILE_TO_STORE_LINKS_TO_ADS = "AlreadyViewedAdsLinks.log"
  private val OUTPUT_HTML_FILE = "NewAds.html"

  private val LINK_HAND_OVER = "https://m.ss.com/ru/real-estate/flats/riga/all/hand_over/"
  private val LINK_SELL = "https://m.ss.com/ru/real-estate/flats/riga/all/sell/"

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
      val l = link + "page" + index + ".html"
      Thread.sleep((Math.random() * 1000).toLong) // avoid spamming ss.com with requests
      print(".")
      if (index > lastPageIndex) {
        println(" DONE!")
        docs
      }
      else {
        mainloop(index + 1, Jsoup.connect(l).get() :: docs)
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

  def handleAll(fileToStoreKnownAds: String, output: String, link: String): Unit = {
    val knownAds = Paths.get(fileToStoreKnownAds)
    if (!Files.exists(knownAds))
      Files.createFile(knownAds)

    val outputPath = Paths.get(output)
    if (!Files.exists(outputPath))
      Files.createFile(outputPath)

    val allPages = getAllPages(link)
    println("Found " + allPages.size + " new pages.")

    val viewedAdsSource = Source.fromFile(fileToStoreKnownAds)
    val viewedAds = HashSet() ++ viewedAdsSource.getLines()
    viewedAdsSource.close()

    allPages.toStream
      .flatMap(extractLinks)
      .map(linkToAdvertisement)
      //.filter(ad => ad.address.district.toLowerCase.equals("кенгарагс"))
      .filter(ad => ad.roomsBetween(2, 3))
      .filter(ad => ad.seria == "119-я" || ad.seria == "103-я" || ad.seria == "Нов.")
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
      .map(ad => List(ad.seria, ad.address.street, ad.floor, ad.rooms, ad.price, ad.originalLink).mkString("\t"))
      .foreach(str => Files.write(outputPath, (str + "\t" + java.time.LocalDate.now + "\n").getBytes, StandardOpenOption.APPEND))
  }

  def linesToSpark(sc: SparkContext, file: String): RDD[(String, String)] = {
    sc.textFile(file).map(l => l.split('\t')).map(arr => (s"${arr(0)}_${arr(1)}_${arr(2)}_${arr(3)}", arr(5))).reduceByKey((_, k2) => k2)
  }

  def findProbableOverlap(sell: String, handOver: String): Unit = {
    val conf = new SparkConf().setAppName("appName").setMaster("local[*]")
    val sc = new SparkContext(conf)
    val groupingSell = linesToSpark(sc, sell)
    val groupingHandOver = linesToSpark(sc, handOver)
    val joined = groupingSell.join(groupingHandOver).collect()

    joined.foreach(str => Files.write(Paths.get("ProbableSellAndHandOver"), (str + "\t" + java.time.LocalDate.now + "\n").getBytes, StandardOpenOption.APPEND, StandardOpenOption.CREATE))
  }

  def main(args: Array[String]): Unit = {
    handleAll(FILE_TO_STORE_KNOWN_ADS, OUTPUT, LINK_SELL)
    handleAll(FILE_TO_STORE_KNOWN_ADS + "_hand_over", OUTPUT + "_hand_over", LINK_HAND_OVER)

    findProbableOverlap(OUTPUT, OUTPUT + "_hand_over")

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
