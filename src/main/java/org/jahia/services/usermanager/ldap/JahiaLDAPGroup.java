/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.usermanager.ldap;

import org.apache.commons.collections.iterators.EnumerationIterator;
import org.jahia.exceptions.JahiaException;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.usermanager.JahiaGroup;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.services.usermanager.jcr.JCRGroup;
import org.jahia.services.usermanager.jcr.JCRGroupManagerProvider;

import java.security.Principal;
import java.util.*;


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

    /** Group's unique identification number */
    protected int mID;

    /** User Member type designation * */
    protected static int mUSERTYPE = 1;

    /** Group Member type designation * */
    protected static int mGROUPTYPE = 2;

    /** Group additional parameters. */
    private Properties mProperties = new Properties ();

    // LDAP dynamic group (groupOfURLs)
    private boolean dynamic;

    private String providerKey;

    /**
     * 
     *
     * @throws JahiaException This class need to access the Services Registry and the DB Pool
     *                        Service. If any of this services can't be accessed, a
     *                        JahiaException is thrown.
     * @param providerKey the provider key
     * @param	siteID The site id
     * @param dynamic
     */
    protected JahiaLDAPGroup (String providerKey, int id, String groupname, String groupKey, int siteID,
                              Map<String, Principal> members, Properties properties, boolean dynamic, boolean preloadedGroups) {

        this.providerKey = providerKey;
        mID = id;
        mGroupname = groupname;
        mGroupKey ="{"+providerKey+"}"+ groupKey;
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
        return (JahiaGroupManagerLDAPProvider) ServicesRegistry.getInstance().getJahiaGroupManagerService().getProvider(providerKey);
    }

    public boolean removeProperty (String key) {
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

    @Override
    public void addMembers(Collection<Principal> principals) {
    }

    public boolean setProperty (String key, String value) {
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
        return providerKey;
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

   public boolean isMember(final Principal principal) {
       Principal user = principal;
       if (!(user instanceof JahiaLDAPUser) && !(user instanceof JahiaLDAPGroup)) {
           return false;
       }
        if (super.isMember(user)) {
            return true;
        }
        if (!preloadedGroups && user instanceof JahiaUser) {
            boolean result = getLDAPProvider().getUserMembership((JahiaUser) principal).contains(getGroupKey());
            membership.put(JahiaUserManagerService.getKey(principal), result);
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
