/**
 * Jahia Enterprise Edition v6.5
 *
 * Copyright (C) 2002-2011 Jahia Solutions Group. All rights reserved.
 *
 * Jahia delivers the first Open Source Web Content Integration Software by combining Enterprise Web Content Management
 * with Document Management and Portal features.
 *
 * The Jahia Enterprise Edition is delivered ON AN "AS IS" BASIS, WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED.
 *
 * Jahia Enterprise Edition must be used in accordance with the terms contained in a separate license agreement between
 * you and Jahia (Jahia Sustainable Enterprise License - JSEL).
 *
 * If you are unsure which license is appropriate for your use, please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.usermanager;

import org.apache.commons.collections.iterators.EnumerationIterator;
import org.jahia.exceptions.JahiaException;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.usermanager.jcr.JCRGroup;
import org.jahia.services.usermanager.jcr.JCRGroupManagerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 *
 * @author Viceic Predrag <Predrag.Viceic@ci.unil.ch>
 * @version 1.0
 */

public class JahiaLDAPGroup extends JahiaGroup {
    
	private static final long serialVersionUID = -2201602968581239379L;

	private static final transient Logger logger = LoggerFactory
            .getLogger(JahiaLDAPGroup.class);

    /** Group's unique identification number */
    protected int mID;

    /** User Member type designation * */
    protected static int mUSERTYPE = 1;

    /** Group Member type designation * */
    protected static int mGROUPTYPE = 2;

    /** Group home page property * */
    private static final String mHOMEPAGE_PROP = "group_homepage";

    /** Group additional parameters. */
    private Properties mProperties = new Properties ();

    // LDAP dynamic group (groupOfURLs)
    private boolean dynamic;

    /**
     * 
     *
     * @throws JahiaException This class need to access the Services Registry and the DB Pool
     *                        Service. If any of this services can't be accessed, a
     *                        JahiaException is thrown.
     * @param	siteID The site id
     * @param dynamic
     */
    protected JahiaLDAPGroup (int id, String groupname, String groupKey, int siteID,
                              Map<String, Principal> members, Properties properties, boolean dynamic, boolean preloadedGroups)
            throws JahiaException {
        ServicesRegistry registry = ServicesRegistry.getInstance ();
        if (registry == null) {
            throw new JahiaException ("Jahia Internal Error",
                    "JahiaLDAPGroup Could not get the Service Registry instance",
                    JahiaException.SERVICE_ERROR, JahiaException.CRITICAL_SEVERITY);
        }

        mID = id;
        mGroupname = groupname;
        mGroupKey ="{"+getLDAPProvider().getKey()+"}"+ groupKey;
        mSiteID = siteID;

        if (preloadedGroups || members != null && members.size() >  0) {
            mMembers = members != null ? new HashSet<Principal>(members.values())
                    : new HashSet<Principal>();
        }

        if (properties != null) {
            mProperties = properties;
        }
        this.dynamic = dynamic;
        this.preloadedGroups = preloadedGroups;
    }

    private JahiaGroupManagerLDAPProvider getLDAPProvider() {
        return ((JahiaGroupManagerLDAPProvider) SpringContextSingleton.getModuleBean("jahiaGroupLDAPProvider"));
    }

    /**
     * Returns the group's home page id.
     * -1 : undefined
     *
     * @return int The group homepage id.
     */

    public int getHomepageID () {
        if (mProperties != null) {

            try {
                // Get the home page from the Jahia DB.
                // By default an external group is represented with a -1 group ID.
                String value = mProperties.getProperty (mHOMEPAGE_PROP);

                if (value == null)
                    return -1;
                return Integer.parseInt (value);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return -1;

    }

    public boolean setHomepageID (int id) {
        if (!removeProperty (mHOMEPAGE_PROP))
            return false;
        // Set the home page into the Jahia DB.
        // By default an external group is represented with a -1 group ID.
        return setProperty (mHOMEPAGE_PROP, String.valueOf (id));

    }

    public synchronized boolean removeProperty (String key) {
        boolean result = false;

        if ((key != null) && (key.length () > 0)) {
            // Remove these lines if LDAP problem --------------------
            JCRGroupManagerProvider userManager = (JCRGroupManagerProvider) SpringContextSingleton.getInstance().getContext().getBean("JCRGroupManagerProvider");
            JCRGroup jcrGroup = (JCRGroup) userManager.lookupExternalGroup(getName());
            if(jcrGroup!=null) {
                jcrGroup.removeProperty(key);
            }
        }

        if (result) {
            mProperties.remove (key);
        }
        // End remove --------------------
        return result;

    }

    public Properties getProperties () {
        return mProperties;
    }

    public boolean removeMember (Principal user) {
        /**@todo Must check this*/
        return false;
    }

    public String getProperty (String key) {
        if ((mProperties != null) && (key != null)) {
            return mProperties.getProperty (key);
        }
        return null;

    }

    public boolean addMember (Principal user) {
        /**@todo Must check this*/
        return false;
    }

    public synchronized boolean setProperty (String key, String value) {
        boolean result = false;

        if ((key != null) && (value != null)) {
            // Remove these lines if LDAP problem --------------------
            JCRGroupManagerProvider userManager = (JCRGroupManagerProvider) SpringContextSingleton.getInstance().getContext().getBean("JCRGroupManagerProvider");
            JCRGroup jcrGroup = (JCRGroup) userManager.lookupExternalGroup(getName());
            if(jcrGroup!=null) {
                jcrGroup.setProperty(key, value);
            }

            // End remove --------------------
            if (result) {
                mProperties.setProperty(key, value);
            }
        }
        return result;

    }

    public String getProviderName () {
        return JahiaGroupManagerLDAPProvider.PROVIDER_NAME;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public String toString () {

        StringBuffer output = new StringBuffer ("Details of group [" + mGroupname + "] :\n");

        output.append ("  - ID : " + Integer.toString (mID) + "\n");

        output.append ("  - properties :");

        @SuppressWarnings("unchecked")
		Iterator<String> names = new EnumerationIterator(mProperties.propertyNames ());
        String name;
        if (names.hasNext ()) {
            output.append ("\n");
            while (names.hasNext ()) {
                name = (String) names.next ();
                output.append (
                        "       " + name + " -> [" + (String) mProperties.getProperty (name) + "]\n");
            }
        } else {
            output.append (" -no properties-\n");
        }

        // Add the user members useranames detail
        output.append ("  - members : ");
        
        if (mMembers != null) {
            if (mMembers.size() > 0) {
                for (Principal member : mMembers) {
                    output.append ((member != null ? member.getName() : null) + "/");
                }
            } else {
                output.append (" -no members-\n");
            }
        } else {
            output.append (" -preloading of members disabled-\n");
        }

        return output.toString ();
    }

    public boolean equals (Object another) {
        if (this == another) return true;

        if (another != null && this.getClass() == another.getClass()) {
            return (getGroupKey().equals(((JahiaGroup) another).getGroupKey()));
        }
        return false;
    }

    //-------------------------------------------------------------------------
    public int hashCode () {
        return mID;
    }


    public void setSiteID (int id) {
        mSiteID = id;
    }

   public boolean isMember(Principal principal) {
       Principal user = principal;
       if (!(user instanceof JahiaLDAPUser) && !(user instanceof JahiaLDAPGroup)) {
           return false;
       }
        if (super.isMember(user)) {
            return true;
        }
        if (!preloadedGroups && user instanceof JahiaUser) {
            boolean result = getLDAPProvider().getUserMembership((JahiaUser)principal).contains(getGroupKey());
            membership.put(JahiaUserManagerService.getKey(principal), Boolean.valueOf(result));
            return result;
        }
        return false;
    }

   @Override
    protected Set<Principal> getMembersMap() {
        if (mMembers == null) {
            mMembers = new HashSet<Principal>(getLDAPProvider().getGroupMembers(getGroupname(), isDynamic()).values());
            preloadedGroups = true;
        }
        return mMembers;
    }
}
