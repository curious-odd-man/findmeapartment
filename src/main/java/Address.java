public class Address {
    private String aCity;
    private String aDistrict;
    private String aStreet;

    public Address(String city, String district, String street) {
        aCity = city;
        aDistrict = district;
        aStreet = street;
    }

    public String getCity() {
        return aCity;
    }

    public String getDistrict() {
        return aDistrict;
    }

    public String getStreet() {
        return aStreet;
    }
}
