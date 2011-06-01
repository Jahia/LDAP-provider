/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2011 Jahia Solutions Group SA. All rights reserved.
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

package org.jahia.services.usermanager;

import java.util.List;

/**
 * An implementation for external Jahia user, which is stored in the LDAP directory.
 * A JahiaUser represents a physical person who is defined by a username and
 * a password for authentication purpose. Every other property of a JahiaUser
 * is stored in it's properties list, which hold key-value string pairs.
 * For example email, firstname, lastname, ... information should be stored in
 * this properties list.
 *
 * @author Serge Huber
 * @version 1.0
 */
public class JahiaLDAPUser extends BaseJahiaExternalUser {
    
    private static final long serialVersionUID = 949596639726348808L;

    /** DN in ldap repository **/
    private String dn;

    /** groups **/
    private List<String> mGroups;

    /**
     * Create a new JahiaLDAPUser class instance. The passed in password must
     * already be encrypted, no encryption will be done. If the passed in
     * properties is null, then the user will have no additional parameter than
     * it's id, name and password.
     *
     * @param providerKey the provider key
     * @param username       User identification name.
     * @param password   User password.
     * @param userKey   user key.
     * @param ldapUserProperties User properties.
     * @param dn the user's DN
     */
    protected JahiaLDAPUser (String providerKey, String username, String userKey, UserProperties ldapUserProperties, String dn)
    {
        super(providerKey, username, "{"+providerKey+"}" + userKey, ldapUserProperties);
        this.dn = dn;
    }

    public String getDN() {
        return dn;
    }

    public boolean setPassword (String password) {
        // not supported by LDAP
        return false;
    }

    public List<String> getGroups() {
        return mGroups;
    }

    public void setGroups(List<String> mGroups) {
        this.mGroups = mGroups;
    }

    @Override
    protected boolean removePropertyExternal(String key) {
        // not supported by LDAP
        return false;
    }

    @Override
    protected boolean setPropertyExternal(String key, String value) {
        // not supported by LDAP
        return false;
    }

}
