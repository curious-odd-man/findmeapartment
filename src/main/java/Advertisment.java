import java.nio.file.Path;

public class Advertisment {

    //   case class Advertisement(text: String, rooms: String, area: String, seria: String, floor: String, price: String, pricePerMeter: String, homeType: String, originalLink: String, address: Address) {
    //    def rooms(min: Int, max: Int): Try[Boolean] = {
    //      Try {
    //        val r = rooms.toInt
    //        min <= r && r <= max
    //      }
    //    }
    //
    //    override def hashCode(): Int = text.hashCode()
    //  }

    private final String  aText;
    private final String  aRooms;
    private final String  aHouseModel;
    private final String  aArea;
    private final String  aFloor;
    private final String  aNumFloors;
    private final String  aPrice;
    private final String  aPricePerMeter;
    private final String  aHomeType;
    private final String  aOriginalLink;
    private final Address aAddress;
    private final Path    aFile;

    public Advertisment(String text, String rooms, String houseModel, String area, String floor, String numFloors, String price, String pricePerMeter, String homeType, String originalLink, Address address, Path file) {
        aText = text;
        aRooms = rooms;
        aHouseModel = houseModel;
        aArea = area;
        aFloor = floor;
        aNumFloors = numFloors;
        aPrice = price;
        aPricePerMeter = pricePerMeter;
        aHomeType = homeType;
        aOriginalLink = originalLink;
        aAddress = address;
        aFile = file;
    }

    public String getText() {
        return aText;
    }

    public String getRooms() {
        return aRooms;
    }

    public String getHouseModel() {
        return aHouseModel;
    }

    public String getArea() {
        return aArea;
    }

    public String getFloor() {
        return aFloor;
    }

    public String getNumFloors() {
        return aNumFloors;
    }

    public String getPrice() {
        return aPrice;
    }

    public String getPricePerMeter() {
        return aPricePerMeter;
    }

    public String getHomeType() {
        return aHomeType;
    }

    public String getOriginalLink() {
        return aOriginalLink;
    }

    public Address getAddress() {
        return aAddress;
    }

    public Path getFile() {
        return aFile;
    }
}
