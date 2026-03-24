package model;

// configurable pharmacy identity — managers can update these via the templates screen
// these values appear on receipts, invoices and reminder letters
public class PharmacyConfig {

    private static String name    = "Cosymed Ltd.";
    private static String address = "3, High Level Drive,";
    private static String city    = "Sydenham,";
    private static String postcode= "SE26 3ET";
    private static String phone   = "Phone: 0208 778 0124";
    private static String fax     = "Fax:   0208 778 0125";
    private static String email   = "";

    public static String getName()     { return name; }
    public static String getAddress()  { return address; }
    public static String getCity()     { return city; }
    public static String getPostcode() { return postcode; }
    public static String getPhone()    { return phone; }
    public static String getFax()      { return fax; }
    public static String getEmail()    { return email; }

    // called from the templates screen when a manager updates settings
    public static void update(String name, String address, String city,
                               String postcode, String phone, String fax, String email) {
        PharmacyConfig.name     = name;
        PharmacyConfig.address  = address;
        PharmacyConfig.city     = city;
        PharmacyConfig.postcode = postcode;
        PharmacyConfig.phone    = phone;
        PharmacyConfig.fax      = fax;
        PharmacyConfig.email    = email;
    }
}
