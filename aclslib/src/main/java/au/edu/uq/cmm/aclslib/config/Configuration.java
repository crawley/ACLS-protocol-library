package au.edu.uq.cmm.aclslib.config;

import java.net.InetAddress;
import java.util.Collection;

/**
 * The combined configuration property API for ACLSProxy and the data grabber.
 * Different implementations support different persistence mechanisms.
 * 
 * @author scrawley
 */
public interface Configuration {

    /**
     * Get the ACLS proxy's port number
     */
    int getProxyPort();

    /**
     * Get the ACLS server's hostname or IP address
     */
    String getServerHost();

    /**
     * Get the ACLS server's port number
     */
    int getServerPort();

    /**
     * Get the 'useProject' flag.  This determines whether ACLS clients 
     * describe the user's ACLS account as an "account" or "project" name.
     */
    boolean isUseProject();

    /**
     * Get the Facility descriptor for the Facility with a given address.
     * 
     * @param addr the alleged Facility's address
     * @return the Facility descriptor or null.
     */
    FacilityConfig lookupFacilityByAddress(InetAddress addr);

    /**
     * Get the Facility descriptor for the Facility with a given name.
     * 
     * @param name the facility name
     * @return the Facility descriptor or null.
     */
    FacilityConfig lookupFacilityByName(String name);

    /**
     * Get the hostname or IP address of the ACLSProxy.
     */
    String getProxyHost();

    /**
     * Get the name of the "dummy facility" that is used for checking
     * ACLS user names and passwords.
     * 
     * @return the facility name or null.
     */
    String getDummyFacility();

    /**
     * Get a collection containing all configured facilities.
     */
    Collection<FacilityConfig> getFacilities();

    /**
     * Get the base URL that will be used for the URLs of files
     * in the ingestion queue.
     */
    String getBaseFileUrl();

    /**
     * Get the pathname of directory where captured (grabbed) files
     * are written.
     */
    String getCaptureDirectory();

    /**
     * Get the URL or IRI for the atom feed.  This is what is used as
     * the "atom:id" for the feed.
     */
    String getFeedId();

    /**
     * Get the string used as the feed's atom:title
     */
    String getFeedTitle();

    /**
     * Get the string used as the feed's author name
     */
    String getFeedAuthor();

    /**
     * Get the string used as the feed's author email.  This can be
     * empty or null.
     */
    String getFeedAuthorEmail();

    /**
     * Get the URL for fetching the feed.
     */
    String getFeedUrl();

    /**
     * Get the page size for the atom feed.  This should be a number greater or
     * equal to 1.  (Setting this to a very large number effectively turns 
     * off feed paging.)
     */
    int getFeedPageSize();

    /**
     * Get the interval (in minutes) between calls to the ACLS server to re-check 
     * for new virtual vMFL facilities.  If the interval is zero or negative,
     * there is no re-checking.
     */
    long getFacilityRecheckInterval();

}