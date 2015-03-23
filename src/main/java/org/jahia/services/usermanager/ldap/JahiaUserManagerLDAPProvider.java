/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2015 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect”, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 */
package org.jahia.services.usermanager.ldap;

import com.sun.jndi.ldap.LdapURL;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaException;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.params.valves.CookieAuthConfig;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.CacheHelper;
import org.jahia.services.cache.ModuleClassLoaderAwareCacheEntry;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.jahia.services.usermanager.*;
import org.jahia.services.usermanager.jcr.JCRUserManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.*;

import java.util.*;

/**
 * An LDAP provider implementation for the management of users.
 *
 * @author Serge Huber
 */
public class JahiaUserManagerLDAPProvider extends JahiaUserManagerProvider {

    public static final String FEATURE = "org.jahia.ldap";

    // the LDAP User cache name.
    public static final String LDAP_USER_CACHE = "LDAPUsersCache";

    public static final String LDAP_NON_EXISTANT_USER_CACHE = "LDAPNonExistantUsersCache";

    /**
     * Root user unique identification number
     */
    public static final int ROOT_USER_ID = 0;

    /**
     * Guest user unique identification number
     */
    public static final int GUEST_USER_ID = 1;

    /**
     * logging
     */
    private static Logger logger = LoggerFactory.getLogger(JahiaUserManagerLDAPProvider.class);

    public static String CONTEXT_FACTORY_PROP = "context.factory";
    public static String LDAP_URL_PROP = "url";
    public static String AUTHENTIFICATION_MODE_PROP =
            "authentification.mode";
    public static String PUBLIC_BIND_DN_PROP = "public.bind.dn";
    public static String PUBLIC_BIND_PASSWORD_PROP =
            "public.bind.password";

    public static String UID_SEARCH_ATTRIBUTE_PROP =
            "uid.search.attribute";
    public static String UID_SEARCH_NAME_PROP = "uid.search.name";
    public static String USERS_OBJECTCLASS_ATTRIBUTE =
            "search.objectclass";

    public static String LDAP_REFFERAL_PROP = "refferal";
    public static String SEARCH_COUNT_LIMIT_PROP =
            "search.countlimit";
    public static String SEARCH_WILDCARD_ATTRIBUTE_LIST =
            "search.wildcards.attributes";

    public static String LDAP_USERNAME_ATTRIBUTE =
            "username.attribute.map";
    public static String USE_CONNECTION_POOL = "ldap.connect.pool";

    public static String CONNECTION_TIMEOUT = "ldap.connect.timeout";

    public static final String DEFAULT_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

    public static final String DEFAULT_AUTHENTIFICATION_MODE = "simple";

    private Map<String, String> ldapProperties = null;

    private Map<String, String> defaultLdapProperties = null;

    private Map<String, String> mappedProperties = null;

    private List<String> searchWildCardAttributeList = null;

    private Ehcache userCache;
    private Ehcache nonExistantUserCache;

    private EhCacheProvider cacheProvider;

    private Map<String, String> overridenLdapProperties;

    private CookieAuthConfig cookieAuthConfig;

    private boolean postponePropertiesInit;

    private String keyPrefix;

    /**
     * Default constructor
     *
     * @throws JahiaException The user manager need some Jahia services to be
     *                        able to run correctly. If one of these services are not instanciated then a
     *                        JahiaException exception is thrown.
     */
    protected JahiaUserManagerLDAPProvider()
            throws JahiaException {
        super();
        initializeDefaults();
    }

    public void setCacheProvider(EhCacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    public void setLdapProperties(Map<String, String> ldapProperties) {
        this.overridenLdapProperties = ldapProperties;
    }

    public void start() {
        // do nothing
    }

    public void stop() {
        // do nothing
    }

    /**
     * This is the method that creates a new user in the system, with all the
     * specified attributes.
     *
     * @param name       User login name.
     * @param password   User password
     * @param properties User additional parameters. If the user has no additional
     *                   attributes, give a NULL pointer to this parameter.
     * @return a JahiaUser object containing an instance of the created user,
     *         in this case a instance of JahiaLDAPUser.
     */
    public JahiaUser createUser(String name,
                                String password,
                                Properties properties) {
        return null;
    }

    //--------------------------------------------------------------------------

    /**
     * This method removes a user from the system. All the user's attributes are
     * remove, and also all the related objects belonging to the user. On success,
     * true is returned and the user parameter is not longer valid. Return false
     * on any failure.
     *
     * @param user reference on the user to be deleted.
     * @return Return true on success, or false on any failure.
     */
    public boolean deleteUser(JahiaUser user) {
        return false;
    }

    /**
     * Return the amount of users in the database.
     *
     * @return The amount of users.
     * @throws JahiaException in case there's a problem retrieving the number
     *                        of users from the storage
     */
    public int getNbUsers() {
        return -1;
    }

    public String getUrl() {
        return ldapProperties.get(LDAP_URL_PROP);
    }

    /**
     * This method return all users' keys in the system.
     *
     * @return Return a List of strings holding the user identification key .
     */
    public List<String> getUserList() {
        List<String> result = new ArrayList<String>();


        DirContext ctx = null;
        try {
            ctx = getPublicContext();
            List<SearchResult> answer = getUsers(ctx, new Properties(), ldapProperties.get(UID_SEARCH_NAME_PROP), SearchControls.SUBTREE_SCOPE);
            for (SearchResult sr : answer) {
                JahiaUser curUser = ldapToJahiaUser(sr);
                if (curUser != null) {
                    result.add(curUser.getUserKey());
                }
            }
        } catch (SizeLimitExceededException slee) {
            // we just return the list as it is
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Search generated more than configured maximum search limit, limiting to " +
                                this.ldapProperties.get(SEARCH_COUNT_LIMIT_PROP) +
                                " first results...");
            }
        } catch (NamingException ne) {
            logger.warn("JNDI warning", ne);
            result = new ArrayList<String>();
        } finally {
            invalidateCtx(ctx);
        }

        return result;
    }

    //--------------------------------------------------------------------------

    /**
     * This method returns the list of all the user names registed into the system.
     *
     * @return Return a List of strings holding the user identification names.
     */
    public List<String> getUsernameList() {
        List<String> result = new ArrayList<String>();

        DirContext ctx = null;
        try {
            ctx = getPublicContext();
            List<SearchResult> answer = getUsers(ctx, new Properties(), ldapProperties.get(UID_SEARCH_NAME_PROP), SearchControls.SUBTREE_SCOPE);
            for (SearchResult sr : answer) {
                JahiaUser curUser = ldapToJahiaUser(sr);
                if (curUser != null) {
                    result.add(curUser.getUsername());
                }
            }
        } catch (SizeLimitExceededException slee) {
            // we just return the list as it is
            if (logger.isDebugEnabled()) {
                logger.debug("Search generated more than configured maximum search limit, limiting to " +
                        this.ldapProperties.get(SEARCH_COUNT_LIMIT_PROP) +
                        " first results...");
            }
        } catch (NamingException ne) {
            logger.warn("JNDI warning", ne);
            result = new ArrayList<String>();
        } finally {
            invalidateCtx(ctx);
        }

        return result;
    }

    /**
     * Retrieves users from the LDAP public repository.
     *
     * @param ctx        the current context in which to search for the user
     * @param filters    a set of name=value string that contain RFC 2254 format
     *                   filters in the value, or null if we want to look in the full repository
     * @param searchBase
     * @param scope
     * @return NamingEnumeration a naming Iterator of SearchResult objects
     *         that contains the LDAP user entries that correspond to the filter
     * @throws NamingException
     */
    private List<SearchResult> getUsers(DirContext ctx, Properties filters, String searchBase, int scope)
            throws NamingException {
        if (ctx == null) {
            throw new NamingException("Context is null !");
        }
        if (filters == null) {
            filters = new Properties();
        }

        int countLimit = Integer.parseInt(ldapProperties.get(JahiaUserManagerLDAPProvider.SEARCH_COUNT_LIMIT_PROP));
        if (filters.containsKey(JahiaUserManagerService.COUNT_LIMIT)) {
            countLimit = Integer.parseInt((String) filters.get(JahiaUserManagerService.COUNT_LIMIT));
        }

        StringBuilder filterString = new StringBuilder();

        if (filters.containsKey("ldap.url")) {
            String url = filters.getProperty("ldap.url");
            try {
                LdapURL ldapURL = new LdapURL(url);
                String thisBase = ldapURL.getDN();
                String thisFilter = ldapURL.getFilter();
                int intScope;
                if ("one".equalsIgnoreCase(ldapURL.getScope())) {
                    intScope = SearchControls.ONELEVEL_SCOPE;
                } else if ("base".equalsIgnoreCase(ldapURL.getScope())) {
                    intScope = SearchControls.OBJECT_SCOPE;
                } else {
                    intScope = SearchControls.SUBTREE_SCOPE;
                }
                if (filters.containsKey("user.key")) {
                    thisFilter = "(&(" + ldapProperties.get(UID_SEARCH_ATTRIBUTE_PROP) + "=" + filters.get("user.key") + ")(" + ldapURL.getFilter() + "))";
                }

                return getUsers(ctx, thisFilter, thisBase, countLimit, intScope);
            } catch (Exception e) {
                logger.error("Cannot get users for url : " + url);
                throw new PartialResultException("Cannot get users for url : " + url);
            }
        } else {
            filterString.append("(&(objectClass=" + StringUtils.defaultString(ldapProperties.get(
                    USERS_OBJECTCLASS_ATTRIBUTE), "*") + ")");

            // let's translate Jahia properties to LDAP properties
            Properties ldapfilters = mapJahiaPropertiesToLDAP(filters);

            String uidFilter = filters.getProperty(ldapProperties.get(UID_SEARCH_ATTRIBUTE_PROP));
            if (uidFilter != null) {
                ldapfilters.put(ldapProperties.get(UID_SEARCH_ATTRIBUTE_PROP), uidFilter);
            }

            int size = filters.size();
            if (filters.containsKey(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)) {
                size = size - 1;
            }
            if (filters.containsKey(JahiaUserManagerService.COUNT_LIMIT)) {
                size = size - 1;
            }
            if (ldapfilters.size() < size) {
                return new ArrayList<SearchResult>();
            }

            if (ldapfilters.size() > 1) {
                boolean orOp = true;
                if (filters.containsKey(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)) {
                    if (((String) filters.get(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)).trim().toLowerCase().equals("and")) {
                        orOp = false;
                    }
                }
                if (orOp) {
                    filterString.append("(|");
                } else {
                    filterString.append("(&");
                }
            }

            Iterator<?> filterKeys = ldapfilters.keySet().iterator();
            while (filterKeys.hasNext()) {
                String filterName = (String) filterKeys.next();
                String filterValue = ldapfilters.getProperty(filterName);
                // we do all the RFC 2254 replacement *except* the "*" character
                // since this is actually something we want to use.
                filterValue = StringUtils.replace(filterValue, "\\",
                        "\\5c");
                filterValue = StringUtils.replace(filterValue, "(",
                        "\\28");
                filterValue = StringUtils.replace(filterValue, ")",
                        "\\29");

                if ("*".equals(filterName)) {
                    // we must match the value for all the attributes
                    // declared in the property file.
                    if (this.searchWildCardAttributeList != null) {
                        if (this.searchWildCardAttributeList.size() > 1) {
                            filterString.append("(|");
                        }
                        Iterator<String> attributeEnum = this.
                                searchWildCardAttributeList.iterator();
                        while (attributeEnum.hasNext()) {
                            String curAttributeName = attributeEnum.next();
                            filterString.append("(");
                            filterString.append(curAttributeName);
                            filterString.append("=");
                            filterString.append(filterValue);
                            filterString.append(")");
                        }
                        if (this.searchWildCardAttributeList.size() > 1) {
                            filterString.append(")");
                        }
                    }
                } else {
                    filterString.append("(");
                    filterString.append(filterName);
                    filterString.append("=");
                    filterString.append(filterValue);
                    filterString.append(")");
                }
            }

            if (ldapfilters.size() > 1) {
                filterString.append(")");
            }

            filterString.append(")");

            return getUsers(ctx, filterString.toString(), searchBase, countLimit, scope);
        }
    }


    /**
     * Maps Jahia user to LDAP properties using the definition
     * mapping in the user LDAP configuration properties file. This modifies
     * the userProps
     *
     * @param userProps
     */
    private Properties mapJahiaPropertiesToLDAP(Properties userProps) {
        if (userProps.size() == 0) {
            return userProps;
        }
        Properties p = new Properties();
        if (userProps.containsKey("*")) {
            p.setProperty("*", userProps.getProperty("*"));
            if (userProps.size() == 1) {
                return p;
            }
        }
        for (Map.Entry<String, String> property : mappedProperties.entrySet()) {
            if (userProps.getProperty(property.getKey()) != null) {
                p.setProperty(property.getValue(), (String) userProps.get(property.getKey()));
            }
        }

        return p;
    }

    /**
     * Returns the internal public context variable. The point of this is to
     * keep this connection open as long as possible, in order to reuser the
     * connection.
     *
     * @return DirContext the current public context.
     * @throws NamingException
     */
    public DirContext getPublicContext() throws NamingException {
        DirContext publicCtx = null;
        publicCtx = connectToPublicDir();
        return publicCtx;
    }


    private DirContext connectToPublicDir()
            throws NamingException {
        // Identify service provider to use
        if (logger.isDebugEnabled()) {
            logger.debug("Attempting connection to LDAP repository on " + ldapProperties.get(LDAP_URL_PROP) + "...");
        }

        Hashtable<String, String> publicEnv = new Hashtable<String, String>(11);
        publicEnv.put(Context.INITIAL_CONTEXT_FACTORY,
                StringUtils.defaultString(ldapProperties.get(CONTEXT_FACTORY_PROP), DEFAULT_CTX_FACTORY));
        publicEnv.put(Context.PROVIDER_URL,
                ldapProperties.get(LDAP_URL_PROP));
        publicEnv.put(Context.SECURITY_AUTHENTICATION,
                StringUtils.defaultString(ldapProperties.get(AUTHENTIFICATION_MODE_PROP), DEFAULT_AUTHENTIFICATION_MODE));
        if (ldapProperties.get(PUBLIC_BIND_DN_PROP) != null) {
            publicEnv.put(Context.SECURITY_PRINCIPAL,
                    ldapProperties.get(PUBLIC_BIND_DN_PROP));
        }
        publicEnv.put(Context.REFERRAL,
                StringUtils.defaultString(ldapProperties.get(LDAP_REFFERAL_PROP), "ignore"));
        // Enable connection pooling
        publicEnv.put("com.sun.jndi.ldap.connect.pool", StringUtils.defaultString(ldapProperties
                .get(USE_CONNECTION_POOL), "true"));
        String timeout = StringUtils.defaultString(ldapProperties.get(CONNECTION_TIMEOUT), "-1");
        if (!timeout.equals("-1") && !timeout.equals("0")) {
            publicEnv.put("com.sun.jndi.ldap.connect.timeout", timeout);
        }

        if (ldapProperties.get(PUBLIC_BIND_PASSWORD_PROP) != null) {
            logger.debug("Using authentification mode to connect to public dir...");
            publicEnv.put(Context.SECURITY_CREDENTIALS,
                    ldapProperties.get(PUBLIC_BIND_PASSWORD_PROP));
        }

        // Create the initial directory context
        return new InitialDirContext(publicEnv);
    }

    /**
     * Translates LDAP attributes to a JahiaUser properties set. Multi-valued
     * attribute values are converted to Strings containing LINEFEED (\n)
     * characters. This way it is quite simple to use String Tokenizers to
     * extract multiple values. Note that if a value ALREADY contains a line
     * feed characters this will cause unexpected behavior.
     *
     * @param sr result of a search on a LDAP directory context
     * @return JahiaLDAPUser a user initialized with the properties loaded
     *         from the LDAP database, or null if no userKey could be determined for
     *         the user.
     */
    private JahiaLDAPUser ldapToJahiaUser(SearchResult sr) {
        Attributes attrs = sr.getAttributes();
        String dn = sr.getName() + "," + ldapProperties.get(UID_SEARCH_NAME_PROP);
        return ldapToJahiaUser(attrs, dn);
    }

    private List<SearchResult> getUsers(DirContext ctx, String filterString, String searchBase, int countLimit, int scope)
            throws NamingException {
        // Search for objects that have those matching attributes
        SearchControls searchCtl = new SearchControls();
        searchCtl.setSearchScope(scope);
        List<SearchResult> answerList = new ArrayList<SearchResult>();
        searchCtl.setCountLimit(countLimit);
        if (logger.isDebugEnabled()) {
            logger.debug("Using filter string [" + filterString.toString() + "]...");
        }
        try {
            NamingEnumeration<SearchResult> enumeration = ctx.search(
                    searchBase,
                    filterString.toString(),
                    searchCtl);
            while (enumeration.hasMoreElements()) {
                answerList.add(enumeration.nextElement());
            }
        } catch (javax.naming.NoInitialContextException nice) {
            logger.warn("Reconnection required", nice);
        } catch (javax.naming.CannotProceedException cpe) {
            logger.warn("Reconnection required", cpe);
        } catch (javax.naming.ServiceUnavailableException sue) {
            logger.warn("Reconnection required", sue);
            throw sue;
        } catch (javax.naming.TimeLimitExceededException tlee) {
            logger.warn("Reconnection required", tlee);
        } catch (javax.naming.CommunicationException ce) {
            logger.warn("Reconnection required", ce);
            throw ce;
        } catch (SizeLimitExceededException e) {
            logger.warn(
                    "User search generated more than configured maximum search limit, limiting to " +
                            countLimit + " first results...");
        }
        return answerList;
    }

    /**
     * Performs a login of the specified user.
     *
     * @param userKey      the user identifier defined in this service properties
     * @param userPassword the password of the user
     * @return String a string that contains the common name of this user
     *         whithin the repository.
     */
    public boolean login(String userKey, String userPassword) {
        String dn = null;

        if ("".equals(userPassword)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Empty passwords are not authorized for LDAP login ! Failing user " + userKey + " login request.");
            }
            return false;
        }

        DirContext privateCtx = null;

        try {
            JahiaLDAPUser jahiaLDAPUser = (JahiaLDAPUser) lookupUserByKey(userKey);
            if (jahiaLDAPUser == null) {
                logger.warn("Couldn't lookup LDAP user by key " + userKey + ", aborting login.");
                return false;
            }
            dn = jahiaLDAPUser.getDN();

            privateCtx = connectToPrivateDir(dn, userPassword);
            if (privateCtx == null) {
                dn = null;
            }
        } catch (javax.naming.CommunicationException ce) {
            logger.warn("CommunicationException", ce);
            logger.debug("Invalidating connection to public LDAP context...");
            dn = null;
        } catch (NamingException ne) {
            if (logger.isDebugEnabled()) {
                logger.debug("Login refused, server message : " + ne.getMessage());
            }
            dn = null;
        } finally {
            invalidateCtx(privateCtx);
        }
        return (dn != null);
    }

    /**
     * Performs a login of the specified user.
     *
     * @param dn           the user DN
     * @param userPassword the password of the user
     * @return true if the user login is successful
     */
    public boolean loginByDN(String dn, String userPassword) {
        if (StringUtils.isEmpty(userPassword)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Empty passwords are not authorized for LDAP login ! Failing user with DN=" + dn + " login request.");
            }
            return false;
        }

        boolean success = false;
        DirContext privateCtx = null;

        try {
            privateCtx = connectToPrivateDir(dn, userPassword);
            success = privateCtx != null;
        } catch (CommunicationException ce) {
            logger.warn(ce.getMessage(), ce);
        } catch (NamingException ne) {
            if (logger.isDebugEnabled()) {
                logger.debug("Login refused, server message : " + ne.getMessage());
            }
        } finally {
            invalidateCtx(privateCtx);
        }

        return success;
    }

    private DirContext connectToPrivateDir(String dn, String personPassword)
            throws NamingException {

        // Identify service provider to use
        Hashtable<String, String> privateEnv = new Hashtable<String, String>(11);
        privateEnv.put(Context.INITIAL_CONTEXT_FACTORY,
                StringUtils.defaultString(ldapProperties.get(CONTEXT_FACTORY_PROP), DEFAULT_CTX_FACTORY));
        privateEnv.put(Context.PROVIDER_URL,
                ldapProperties.get(LDAP_URL_PROP));
        privateEnv.put(Context.SECURITY_AUTHENTICATION,
                StringUtils.defaultString(ldapProperties.get(AUTHENTIFICATION_MODE_PROP), DEFAULT_AUTHENTIFICATION_MODE));
        privateEnv.put(Context.SECURITY_PRINCIPAL, dn);
        privateEnv.put(Context.SECURITY_CREDENTIALS,
                personPassword);

        // Create the initial directory context
        return new InitialDirContext(privateEnv);
    }

    private void invalidateCtx(DirContext ctx) {
        if (ctx == null) {
            logger.debug("Context passed is null, ignoring it...");
            return;
        }
        try {
            ctx.close();
            ctx = null;
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
    }

    //--------------------------------------------------------------------------

    /**
     * Load all the user data and attributes. On success a reference on the user
     * is returned, otherwise NULL is returned.
     *
     * @param userKey User's identification name.
     * @return a reference on a new created jahiaUser object.
     */
    public JahiaUser lookupUserByKey(String userKey, String searchAttributeName) {
        if (!userKey.startsWith(keyPrefix)) {
            return null;
        }

        String cacheKey = getKey() +"k" + userKey;
        JahiaUser user = (JahiaUser) CacheHelper.getObjectValue(userCache, cacheKey);

        if (user == null) {

            if (nonExistantUserCache.get(cacheKey) != null) {
                return null;
            }

            try {
                user = lookupUserInLDAP(removeKeyPrefix(userKey), searchAttributeName);
            } catch (ServiceUnavailableException e) {
                logger.warn("Service unavailable detected while trying to load user " + userKey + ". Returning null and not caching in non existant user cache", e);
                return null;
            } catch (CommunicationException e) {
                logger.warn("Communications exception detected while trying to load user " + userKey + ". Returning null and not caching in non existant user cache", e);
                return null;
            }

            if (user != null) {
                cachePut(cacheKey, user);
                cachePut(getKey() + "n" + user.getUsername(), user);
                cachePut(getKey() + "d" + ((JahiaLDAPUser) user).getDN(), user);
            } else {
                nonExistantUserCache.put(new Element(cacheKey, true));
            }
        }
        return user;
    }

    /**
     * This method is the rigth way to rtrieve a user from the information stored in the member attribute in groups.
     * For example, if the member attribute of a group contains the distinguishedName of the memebers,
     * you have to use lookupUserInLDAP (userDn, "distinguishedName").
     * If your properties file are correctly defined, you could use the value of the
     * JahiaGroupManagerLDAPProvider.SEARCH_USER_ATTRIBUTE_NAME for searchAttributeName.
     * <p);
     * This method is only called by lookupUser (String userKey, String searchAttributeName)
     * which was only called by JahiaGroupManagerLDAPProvider.getGroupMembers()
     */
    private JahiaLDAPUser lookupUserInLDAP(String userKey, String searchAttributeName) throws ServiceUnavailableException, CommunicationException {
        JahiaLDAPUser user = null;

        DirContext ctx = null;
        try {
            ctx = getPublicContext();
            SearchResult sr = getPublicUser(ctx, searchAttributeName, userKey);
            if (sr == null) {
                return null;
            }
            user = ldapToJahiaUser(sr);
        } catch (SizeLimitExceededException slee) {
            if (logger.isDebugEnabled()) {
                logger.debug("Search generated more than configured maximum search limit, limiting to " +
                        this.ldapProperties.get(SEARCH_COUNT_LIMIT_PROP) +
                        " first results...");
            }
            user = null;
        } catch (ServiceUnavailableException sue) {
            // we re-throw so that we are not caught by the more general NamingException clause
            throw sue;
        } catch (CommunicationException ce) {
            // we re-throw so that we are not caught by the more general NamingException clause
            throw ce;
        } catch (NamingException ne) {
            logger.warn("JNDI warning", ne);
            user = null;
        } finally {
            invalidateCtx(ctx);
        }
        return user;
    }

    public JahiaLDAPUser lookupUserFromDN(String dn) {
        if (logger.isDebugEnabled()) {
            logger.debug("Lookup user from dn " + dn);
        }
        JahiaLDAPUser user = null;
        String cacheKeyByDn = getKey() + "d" + dn;
        JahiaLDAPUser result = (JahiaLDAPUser) CacheHelper.getObjectValue(userCache, cacheKeyByDn);
        if (result != null) {
            return result;
        } else {
            if (nonExistantUserCache.get(cacheKeyByDn) != null) {
                return null;
            }
        }
        DirContext ctx = null;
        try {
            ctx = getPublicContext();
            Attributes attributes = getUser(ctx, dn);
            user = ldapToJahiaUser(attributes, dn);
            if (user != null) {
                cachePut(cacheKeyByDn, user);
                cachePut(getKey() + "k" + user.getUserKey(), user);
                cachePut(getKey() + "n" + user.getUsername(), user);
            } else {
                nonExistantUserCache.put(new Element(cacheKeyByDn, true));
            }
        } catch (NameNotFoundException nnfe) {
            user = null;
            nonExistantUserCache.put(new Element(cacheKeyByDn, true));
        } catch (NamingException ne) {
            logger.warn("JNDI warning", ne);
            user = null;
        } finally {
            invalidateCtx(ctx);
        }
        return user;
    }

    private Attributes getUser(DirContext ctx, String dn)
            throws NamingException {
        Attributes attributes = null;
        try {
            if (dn != null && dn.indexOf('/') != -1) {
                dn = StringUtils.replace(dn, "/", "\\/");
            }
            attributes = ctx.getAttributes(dn);
        } catch (javax.naming.NoInitialContextException nice) {
            logger.debug("Reconnection required", nice);
        } catch (javax.naming.CannotProceedException cpe) {
            logger.debug("Reconnection required", cpe);
        } catch (javax.naming.ServiceUnavailableException sue) {
            logger.debug("Reconnection required", sue);
        } catch (javax.naming.TimeLimitExceededException tlee) {
            logger.debug("Reconnection required", tlee);
        } catch (javax.naming.CommunicationException ce) {
            logger.debug("Reconnection required", ce);
        }
        return attributes;
    }

    private JahiaLDAPUser ldapToJahiaUser(Attributes attrs, String dn) {
        JahiaLDAPUser user = null;
        UserProperties userProps = new UserProperties();
        String usingUserKey = null;

        Enumeration<?> attrsEnum = attrs.getAll();
        while (attrsEnum.hasMoreElements()) {
            Attribute curAttr = (Attribute) attrsEnum.nextElement();
            String attrName = curAttr.getID();
            StringBuilder attrValueBuf = new StringBuilder();
            try {
                Enumeration<?> curAttrValueEnum = curAttr.getAll();
                while (curAttrValueEnum.hasMoreElements()) {
                    Object curAttrValueObj = curAttrValueEnum.nextElement();
                    if ((curAttrValueObj instanceof String)) {
                        attrValueBuf.append((String) curAttrValueObj);
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Converting attribute <" + attrName +
                                    "> from class " +
                                    curAttrValueObj.getClass().toString() +
                                    " to String...");
                        }
                        /** @todo FIXME : for the moment we convert everything to String */
                        attrValueBuf.append(curAttrValueObj);
                    }
                    attrValueBuf.append('\n');
                }
            } catch (NamingException ne) {
                logger.warn("JNDI warning", ne);
                attrValueBuf = new StringBuilder();
            }
            String attrValue = attrValueBuf.toString();
            if (attrValue.endsWith("\n")) {
                attrValue = attrValue.substring(0, attrValue.length() - 1);
            }
            if ((attrName != null) && (attrValue != null)) {
                if (usingUserKey == null) {
                    if (attrName.equalsIgnoreCase(
                            ldapProperties.get(UID_SEARCH_ATTRIBUTE_PROP))) {
                        int multiValueMarkerPos = attrValue.indexOf('\n');
                        if (multiValueMarkerPos != -1) {
                            // we have detected a multi-valued UID_SEARCH_ATTRIBUTE_PROP, we will take only
                            // the first value for the user key.
                            usingUserKey = attrValue.substring(0, multiValueMarkerPos);
                        } else {
                            usingUserKey = attrValue;
                        }
                    }
                }
                // mark user property as read-only as it is coming from LDAP
                UserProperty curUserProperty = new UserProperty(attrName, attrValue, true);
                userProps.setUserProperty(attrName, curUserProperty);
            }
        }
        if (usingUserKey != null) {
            // FIXME : Quick hack for merging Jahia DB user properties with LDAP user
//            mapDBToJahiaProperties (userProps, JahiaLDAPUser.USERKEY_LDAP_PREFIX + usingUserKey);
            /* EP : changes the code to handle the name of the user as defined in properties file.
            The name of the user is the value of the LDAP_USERNAME_ATTRIBUTE properties */
            String name = usingUserKey;

            if (ldapProperties.get(LDAP_USERNAME_ATTRIBUTE) != null
                    && ldapProperties.get(LDAP_USERNAME_ATTRIBUTE).length() > 0) {
                name = userProps
                        .getProperty(ldapProperties.get(LDAP_USERNAME_ATTRIBUTE));
            }

            userProps = mapLDAPToJahiaProperties(userProps);
            user = new JahiaLDAPUser(getKey(), name, usingUserKey, userProps, dn);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Ignoring entry " + dn +
                        " because it has no valid " +
                        ldapProperties.get(UID_SEARCH_ATTRIBUTE_PROP) +
                        " attribute to be mapped onto user key...");
            }
        }

        return user;
    }

    /**
     * Map LDAP properties to Jahia user properties, such as first name,
     * last name, etc...
     * This method modifies the userProps object passed on parameters to add
     * the new properties.
     *
     * @param userProps User properties to check for mappings. Basically what
     *                  we do is copy LDAP properties to standard Jahia properties. This is
     *                  defined in the user ldap properties file. Warning this object is modified
     *                  by this method !
     * @todo FIXME : if properties exist in LDAP that have the same name as
     * Jahia properties these will be erased. We should probably look into
     * making the properties names more unique such as org.jahia.propertyname
     */
    private UserProperties mapLDAPToJahiaProperties(UserProperties userProps) {
        UserProperties p = new UserProperties();
        // copy attribute to standard Jahia properties if they exist both in
        // the mapping and in the repository
        for (Map.Entry<String, String> prop : mappedProperties.entrySet()) {
            if (userProps.getUserProperty(prop.getValue()) != null) {
                UserProperty sourceProp = userProps.getUserProperty(prop.getValue());
                UserProperty targetProp = new UserProperty(prop.getKey(), sourceProp.getValue(), sourceProp.isReadOnly());
                p.setUserProperty(prop.getKey(), targetProp);
            } else {
                // for properties that don't have a value in LDAP, we still
                // create a read-only Jahia property, in case it is added
                // later in LDAP. We don't want to authorize edition of an
                // LDAP-mapped property.
                UserProperty targetProp = new UserProperty(prop.getKey(), "", true);
                p.setUserProperty(prop.getKey(), targetProp);
            }
        }

        return p;
    }

    /**
     * Find users according to a table of name=value properties. If the left
     * side value is "*" for a property then it will be tested against all the
     * properties. ie *=test* will match every property that starts with "test"
     *
     * @param searchCriterias a Properties object that contains search criterias
     *                        in the format name,value (for example "*"="*" or "username"="*test*") or
     *                        null to search without criterias
     * @return Set a set of JahiaUser elements that correspond to those
     *         search criterias, or an empty one if an error has occured. Note this will
     *         only return the configured limit of users at maxium. Check out the
     *         users.ldap.properties file to change the limit.
     */
    public Set<JahiaUser> searchUsers(Properties searchCriterias) {
        Set<JahiaUser> result = new HashSet<JahiaUser>();
        // first let's lookup the user by the properties in Jahia's DB
        Set<String> userKeys = searchLDAPUsersByDBProperties(searchCriterias);
        // now that we have the keys, let's load all the users.
        for (String userKey : userKeys) {
            JahiaUser user = lookupUserByKey(userKey);
            if (user != null) {
                result.add(user);
            }
        }

        if (searchCriterias != null && searchCriterias.size() == 1
                && searchCriterias.containsKey(cookieAuthConfig.getUserPropertyName())) {
            return result;
        }

        // now let's lookup in LDAP properties.
        DirContext ctx = null;
        try {
            ctx = getPublicContext();
            List<SearchResult> ldapUsers = getUsers(ctx,
                    searchCriterias, ldapProperties.get(UID_SEARCH_NAME_PROP), SearchControls.SUBTREE_SCOPE);
            for (SearchResult sr : ldapUsers) {
                JahiaLDAPUser user = ldapToJahiaUser(sr);
                if (user != null) {
                    result.add(user);
                }
            }
        } catch (PartialResultException pre) {
            logger.warn(pre.getMessage(), pre);
        } catch (SizeLimitExceededException slee) {
            // logger.error(slee);
            // we just return the list as it is
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Search generated more than configured maximum search limit, limiting to " +
                                this.ldapProperties.get(SEARCH_COUNT_LIMIT_PROP) +
                                " first results...");
            }
        } catch (NamingException ne) {
            logger.warn("JNDI warning", ne);
            result = new HashSet<JahiaUser>();
        } finally {
            invalidateCtx(ctx);
        }
        return result;
    }

    /**
     * Find users according to a table of name=value properties. If the left
     * side value is "*" for a property then it will be tested against all the
     * properties. ie *=test* will match every property that starts with "test"
     *
     * @param searchCriterias a Properties object that contains search criteria
     *                        in the format name,value (for example "*"="*" or "username"="*test*") or
     *                        null to search without criteria
     * @return Set a set of JahiaUser elements that correspond to those
     *         search criteria
     */
    private Set<String> searchLDAPUsersByDBProperties(Properties searchCriterias) {
        if (searchCriterias == null) {
            return Collections.emptySet();
        }

        boolean allEmpty = true;
        for (Object propValue : searchCriterias.values()) {
            String val = String.valueOf(propValue);
            if (propValue != null && val.length() > 0 && !"*".equals(val)) {
                allEmpty = false;
                break;
            }
        }
        if (allEmpty) {
            return Collections.emptySet();
        }
        Set<JahiaUser> users = JCRUserManagerProvider.getInstance().searchUsers(searchCriterias, true, getKey());
        if (users.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> userkeys = new HashSet<String>(users.size());
        for (JahiaUser user : users) {
            userkeys.add(keyPrefix + user.getUsername());
        }

        return userkeys;
    }

    //--------------------------------------------------------------------------

    /**
     * Load all the user data and attributes. On success a reference on the user
     * is returned, otherwise NULL is returned.
     *
     * @param userKey User's identification name.
     * @return a reference on a new created jahiaUser object.
     */
    public JahiaUser lookupUserByKey(String userKey) {
        if (!userKey.startsWith(keyPrefix)) {
            return null;
        }

        final String cacheKey = getKey() + "k" + userKey;
        JahiaUser user = (JahiaUser) CacheHelper.getObjectValue(userCache, cacheKey);

        if (user == null) {
            // then look into the non existent cache
            if (nonExistantUserCache.get(cacheKey) != null) {
                return null;
            }

            try {
                user = lookupUserInLDAP(removeKeyPrefix(userKey));
            } catch (ServiceUnavailableException e) {
                logger.warn("Service unavailable detected while trying to load user " + userKey + ". Returning null and not caching in non existant user cache", e);
                return null;
            } catch (CommunicationException e) {
                logger.warn("Communications exception detected while trying to load user " + userKey + ". Returning null and not caching in non existant user cache", e);
                return null;
            }

            if (user != null) {
                cachePut(cacheKey, user);
                cachePut(getKey() + "n" + user.getUsername(), user);
                cachePut(getKey() + "d" + ((JahiaLDAPUser) user).getDN(), user);
            } else {
                nonExistantUserCache.put(new Element(cacheKey, true));
            }
        }

        return user;
    }

    private JahiaLDAPUser lookupUserInLDAP(String userKey) throws ServiceUnavailableException, CommunicationException {
        JahiaLDAPUser user = null;

        DirContext ctx = null;
        try {
            ctx = getPublicContext();
            SearchResult sr = getPublicUser(ctx, ldapProperties.get(UID_SEARCH_ATTRIBUTE_PROP), userKey);
            if (sr == null) {
                return null;
            }
            user = ldapToJahiaUser(sr);
        } catch (SizeLimitExceededException slee) {
            if (logger.isDebugEnabled()) {
                logger.debug("Search generated more than configured maximum search limit, limiting to " +
                        this.ldapProperties.get(SEARCH_COUNT_LIMIT_PROP) +
                        " first results...");
            }
            user = null;
        } catch (ServiceUnavailableException sue) {
            // we re-throw so that we are not caught by the more general NamingException clause
            throw sue;
        } catch (CommunicationException ce) {
            // we re-throw so that we are not caught by the more general NamingException clause
            throw ce;
        } catch (NamingException ne) {
            logger.warn("JNDI warning", ne);
            user = null;
        } finally {
            invalidateCtx(ctx);
        }
        return user;
    }

    /**
     * Retrieves a user from the LDAP public repository.
     *
     * @param ctx  the current context in which to search for the user
     * @param prop
     * @param val  the unique identifier for the user
     * @return a SearchResult object, which is the *first* result matching the
     *         uid
     * @throws NamingException
     */
    private SearchResult getPublicUser(DirContext ctx, String prop, String val)
            throws NamingException {
        Properties filters = new Properties();

        filters.setProperty(prop, val);
        List<SearchResult> answer = getUsers(ctx, filters, ldapProperties.get(UID_SEARCH_NAME_PROP), SearchControls.SUBTREE_SCOPE);
        SearchResult sr = null;
        if (!answer.isEmpty()) {
            // we only take the first value if there are multiple answers, which
            // should normally NOT happend if the uid is unique !!
            sr = answer.get(0);
            if (answer.size() > 1) {                // there is at least a second result.
                logger.debug(
                        "Warning : multiple users with same UID in LDAP repository.");
            }
        }
        return sr;
    }

    private String removeKeyPrefix(String userKey) {
        if (userKey.startsWith(keyPrefix)) {
            return userKey.substring(keyPrefix.length());
        } else {
            return userKey;
        }
    }

    public void updateCache(JahiaUser jahiaUser) {
        String cacheKey = getKey() + "k" + jahiaUser.getUserKey();
        String cacheKeyByName = getKey() + "n" + jahiaUser.getUsername();
        userCache.remove(cacheKey);
        userCache.remove(cacheKeyByName);
        nonExistantUserCache.remove(cacheKey);
        nonExistantUserCache.remove(cacheKeyByName);
        if (jahiaUser instanceof JahiaLDAPUser) {
            JahiaLDAPUser jahiaLDAPUser = (JahiaLDAPUser) jahiaUser;
            nonExistantUserCache.remove(getKey() + "d" + jahiaLDAPUser.getDN());
        }
    }

    //--------------------------------------------------------------------------

    /**
     * This function checks into the system if the name has already been
     * assigned to another user.
     *
     * @param name User login name.
     * @return Return true if the specified name has not been assigned yet,
     *         return false on any failure.
     */
    public boolean userExists(String name) {
        // try to avoid a NullPointerException
        if (name == null) {
            return false;
        }

        // name should not be empty.
        if (name.length() == 0) {
            return false;
        }

        return (lookupUser(name) != null);
    }

    // @author  NK

    /**
     * Load all the user data and attributes. On success a reference on the user
     * is returned, otherwise NULL is returned.
     *
     * @param name User's identification name.
     * @return Return a reference on a new created jahiaUser object.
     */
    public JahiaUser lookupUser(String name) {
        return lookupUserByKey(keyPrefix + name);
    }

    public Map<String, String> getLdapProperties() {
        return ldapProperties;
    }

    public void setDefaultLdapProperties(Map<String, String> globalLdapProperties) {
        this.defaultLdapProperties = globalLdapProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!postponePropertiesInit) {
            try {
                initProperties();
            } catch (JahiaInitializationException e) {
                logger.error("A problem occured during properties initialization", e);
            }
        }
    }

    public void initProperties() throws JahiaInitializationException {
        if (defaultLdapProperties == null) {
            defaultLdapProperties = new HashMap<String, String>();
        }

        ldapProperties = defaultLdapProperties != null ? new HashMap<String, String>(defaultLdapProperties) : new HashMap<String, String>();
        if (overridenLdapProperties != null) {
            ldapProperties.putAll(overridenLdapProperties);
        }
        if (ldapProperties.containsKey("priority")) {
            setPriority(Integer.parseInt(ldapProperties.get("priority")));
        }

        if (userManagerService != null) {
            userManagerService.registerProvider(this);
        }


        if (!ldapProperties.containsKey(LDAP_USERNAME_ATTRIBUTE)) {
            ldapProperties.put(LDAP_USERNAME_ATTRIBUTE,
                    ldapProperties.get(UID_SEARCH_ATTRIBUTE_PROP));
        }

        this.mappedProperties = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : ldapProperties.entrySet()) {
            if (entry.getKey().endsWith(".attribute.map")) {
                mappedProperties.put(StringUtils.substringBeforeLast(entry.getKey(),
                        ".attribute.map"), entry.getValue());
            }
        }

        if (cacheProvider == null) {
            cacheProvider = (EhCacheProvider) SpringContextSingleton.getBean("ehCacheProvider");
        }
        if (cookieAuthConfig == null) {
            cookieAuthConfig = (CookieAuthConfig) SpringContextSingleton.getBean("cookieAuthConfig");
        }

        final CacheManager cacheManager = cacheProvider.getCacheManager();
        userCache = cacheManager.getCache(LDAP_USER_CACHE);
        if (userCache == null) {
            cacheManager.addCache(LDAP_USER_CACHE);
            userCache = cacheManager.getCache(LDAP_USER_CACHE);
        }
        nonExistantUserCache = cacheManager.getCache(LDAP_NON_EXISTANT_USER_CACHE);
        if (nonExistantUserCache == null) {
            cacheManager.addCache(LDAP_NON_EXISTANT_USER_CACHE);
            nonExistantUserCache = cacheManager.getCache(LDAP_NON_EXISTANT_USER_CACHE);
        }

        String wildCardAttributeStr = ldapProperties.get(
                JahiaUserManagerLDAPProvider.SEARCH_WILDCARD_ATTRIBUTE_LIST);
        if (wildCardAttributeStr != null) {
            this.searchWildCardAttributeList = new ArrayList<String>();
            StringTokenizer wildCardTokens = new StringTokenizer(
                    wildCardAttributeStr, ", ");
            while (wildCardTokens.hasMoreTokens()) {
                String curAttrName = wildCardTokens.nextToken().trim();
                this.searchWildCardAttributeList.add(curAttrName);
            }
        }

        logger.debug("Initialized and connected to public repository");
    }

    public void unregister() {
        if (userManagerService != null) {
            userManagerService.unregisterProvider(this);
        }
    }

    public void setCookieAuthConfig(CookieAuthConfig cookieAuthConfig) {
        this.cookieAuthConfig = cookieAuthConfig;
    }

    private void initializeDefaults() {
        setKey("ldap");
        setPriority(2);
        setReadOnly(true);
        defaultLdapProperties = iniDefaultProperties();
    }

    private Map<String, String> iniDefaultProperties() {
        HashMap<String, String> props = new HashMap<String, String>();

        // Connection and authentication parameters
        props.put(CONTEXT_FACTORY_PROP, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(AUTHENTIFICATION_MODE_PROP, "simple");
        props.put(USE_CONNECTION_POOL, "true");
        props.put(CONNECTION_TIMEOUT, "5000");

        // Search parameters
        props.put(SEARCH_COUNT_LIMIT_PROP, "100");
        props.put(UID_SEARCH_ATTRIBUTE_PROP, "cn");
        props.put(USERS_OBJECTCLASS_ATTRIBUTE, "person");
        props.put(SEARCH_WILDCARD_ATTRIBUTE_LIST, "ou, cn, o, c, mail, uid, uniqueIdentifier, givenName, sn, dn");

        // property mapping
        props.put("j:firstName.attribute.map", "givenName");
        props.put("j:lastName.attribute.map", "sn");
        props.put("j:email.attribute.map", "mail");
        props.put("j:organization.attribute.map", "ou");

        return props;
    }

    public void setPostponePropertiesInit(boolean postponePropertiesInit) {
        this.postponePropertiesInit = postponePropertiesInit;
    }

    @Override
    public void setKey(String key) {
        super.setKey(key);
        keyPrefix = "{" + key + "}";
    }
    
    protected void cachePut(String key, JahiaUser user) {
        userCache.put(new Element(key, new ModuleClassLoaderAwareCacheEntry(user, "ldap")));
    }
}

