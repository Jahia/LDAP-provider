/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
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
package org.jahia.services.usermanager.ldap.config;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Abstract config provide by the ldap config file
 * @author kevan
 */
public abstract class AbstractConfig {
    private String url;
    private String publicBindDn;
    private String publicBindPassword;
    private String authentificationMode = "simple";
    private String contextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
    private boolean ldapConnectPool = true;
    private long ldapConnectTimeout = 5000;
    private long searchCountlimit = 100;
    private String searchObjectclass;
    private Set<String> searchWildcardsAttributes = Sets.newHashSet();
    private Map<String, String> attributesMapper = Maps.newHashMap();

    public void setUrl(String url) {
        this.url = url;
    }

    public void setPublicBindDn(String publicBindDn) {
        this.publicBindDn = publicBindDn;
    }

    public void setPublicBindPassword(String publicBindPassword) {
        this.publicBindPassword = publicBindPassword;
    }

    public void setAuthentificationMode(String authentificationMode) {
        this.authentificationMode = authentificationMode;
    }

    public void setContextFactory(String contextFactory) {
        this.contextFactory = contextFactory;
    }

    public void setLdapConnectPool(boolean ldapConnectPool) {
        this.ldapConnectPool = ldapConnectPool;
    }

    public void setLdapConnectTimeout(long ldapConnectTimeout) {
        this.ldapConnectTimeout = ldapConnectTimeout;
    }

    public String getUrl() {
        return url;
    }

    public String getPublicBindDn() {
        return publicBindDn;
    }

    public String getPublicBindPassword() {
        return publicBindPassword;
    }

    public String getAuthentificationMode() {
        return authentificationMode;
    }

    public String getContextFactory() {
        return contextFactory;
    }

    public boolean isLdapConnectPool() {
        return ldapConnectPool;
    }

    public long getLdapConnectTimeout() {
        return ldapConnectTimeout;
    }

    public Set<String> getSearchWildcardsAttributes() {
        return searchWildcardsAttributes;
    }

    public void setSearchWildcardsAttributes(Set<String> searchWildcardsAttributes) {
        this.searchWildcardsAttributes = searchWildcardsAttributes;
    }

    public Map<String, String> getAttributesMapper() {
        return attributesMapper;
    }

    public void setAttributesMapper(Map<String, String> attributesMapper) {
        this.attributesMapper = attributesMapper;
    }

    public String getSearchObjectclass() {
        return searchObjectclass;
    }

    public void setSearchObjectclass(String searchObjectclass) {
        this.searchObjectclass = searchObjectclass;
    }

    public long getSearchCountlimit() {
        return searchCountlimit;
    }

    public void setSearchCountlimit(long searchCountlimit) {
        this.searchCountlimit = searchCountlimit;
    }
}
