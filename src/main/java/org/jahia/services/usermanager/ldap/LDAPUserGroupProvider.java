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
package org.jahia.services.usermanager.ldap;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.modules.external.users.Member;
import org.jahia.modules.external.users.UserGroupProvider;
import org.jahia.modules.external.users.UserNotFoundException;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserImpl;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.util.*;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * Created by david on 16/09/14.
 * Implementation of UserGroupProvider for Spring LDAP
 */
public class LDAPUserGroupProvider implements UserGroupProvider {

    private ExternalUserGroupService externalUserGroupService;


    // the LDAP User cache name.
    public static final String LDAP_USER_CACHE = "LDAPUsersCache";

    public static final String LDAP_NON_EXISTANT_USER_CACHE = "LDAPNonExistantUsersCache";

    // the LDAP Group cache name.
    public static final String LDAP_GROUP_CACHE = "LDAPGroupsCache";

    public static final String LDAP_NONEXISTANT_GROUP_CACHE = "LDAPNonExistantGroupsCache";

    public static String LDAP_USERNAME_ATTRIBUTE = "username.attribute.map";

    public static String UID_SEARCH_NAME_PROP = "uid.search.name";


    private Map<String, String> groupProperties;
    private Map<String, String> userProperties;
    private String key;
    private List<String> groupCache = new ArrayList<String>();

    // todo : handle properly caches
    private Map<String,List<Member>> groupMembersCache = new HashMap<String, List<Member>>();
    private Map<Properties,List<String>> groupsCache = new HashMap<Properties, List<String>>();
    private LdapTemplate ldapTemplate;

    @Override
    public JahiaUser getUser(String name) throws UserNotFoundException {
        return ldapTemplate.search(query().base(userProperties.get(UID_SEARCH_NAME_PROP)).where("cn").is(name),new JahiaUserAttributesMapper()).get(0);
    }

    @Override
    public boolean groupExists(String name) {
        if (groupCache != null && groupCache.contains(name)) {
            return true;
        } else {
            if (!ldapTemplate.search(
                    query().base(userProperties.get(UID_SEARCH_NAME_PROP)).where("objectclass").is("groupOfUniqueNames").and("cn").is(name),
                    new AttributesMapper<String>() {
                        public String mapFromAttributes(Attributes attrs)
                                throws NamingException {
                            return attrs.get("cn").get().toString();
                        }
                    }).isEmpty()) {
                if (groupCache == null)  {
                    groupCache = new ArrayList<String>();
                }
                groupCache.add(name);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Member> getGroupMembers(String groupName) {
        if (groupMembersCache.containsKey(groupName)) {
            return groupMembersCache.get(groupName);
        }
        String members = ldapTemplate.search(
                query().base(userProperties.get(UID_SEARCH_NAME_PROP)).where("objectclass").is("groupOfUniqueNames").and("cn").is(groupName),
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get("uniqueMember").toString();
                    }
                }).get(0);
        List<Member> memberList = new ArrayList<Member>();
        for (String member : StringUtils.split(members,",")) {
            String userName = StringUtils.substringAfter(member, "cn=");
            memberList.add(new Member(userName,groupExists(userName)? Member.MemberType.GROUP: Member.MemberType.USER));
        }
        groupMembersCache.put(groupName,memberList);
        return memberList;
    }

    @Override
    public List<String> getMembership(String userName) {
        return new ArrayList<String>();
    }

    @Override
    public List<String> searchUsers(Properties searchCriterias) {
        return ldapTemplate.search(
                query().base(userProperties.get(UID_SEARCH_NAME_PROP)).where("objectclass").is("person"),
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get("cn").get().toString();
                    }
                });
    }

    @Override
    public List<String> searchGroups(Properties searchCriterias) {
        if (groupsCache.containsKey(searchCriterias)) {
            return groupsCache.get(searchCriterias);
        }
        List<String> groups = ldapTemplate.search(
                query().base(userProperties.get(UID_SEARCH_NAME_PROP)).where("objectclass").is("groupOfUniqueNames"),
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get("cn").get().toString();
                    }
                });
        groupsCache.put(searchCriterias,groups);
        return groups;
    }

    /**
     * defines the LdapTemplate for this provider
     * @param ldapTemplate
     */
    public void setLdapTemplate(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
        // register the provider
        externalUserGroupService.register(key,this);
    }

    public void unregister() {
        externalUserGroupService.unregister(key);
    }

    @Override
    public boolean verifyPassword(String userName, String userPassword) {
        DirContext ctx = null;
        try {
            String userDn = LdapNameBuilder.newInstance().add("cn", userName).build().toString();
            ctx = ldapTemplate.getContextSource().getContext(userDn, userPassword);
            // Take care here - if a base was specified on the ContextSource
            // that needs to be removed from the user DN for the lookup to succeed.
            ctx.lookup(userDn);
            return true;
        } catch (Exception e) {
            // Context creation failed - authentication did not succeed
            //logger.error("Login failed", e);
            return false;
        } finally {
            // It is imperative that the created DirContext instance is always closed
            LdapUtils.closeContext(ctx);
        }
    }


    public void setGroupProperties(Map<String, String> groupProperties) {
        this.groupProperties = groupProperties;
    }

    public void setUserProperties(Map<String, String> userProperties) {
        this.userProperties = userProperties;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setExternalUserGroupService(ExternalUserGroupService externalUserGroupService) {
        this.externalUserGroupService = externalUserGroupService;
    }

    private class JahiaUserAttributesMapper implements AttributesMapper<JahiaUser> {
        public JahiaUser mapFromAttributes(Attributes attrs) throws NamingException {
            Properties props = new Properties();
            for (String propertyKey : userProperties.keySet()) {
                if (StringUtils.endsWith(propertyKey,"attribute.map")) {
                    String propertyName = StringUtils.substringBefore(".attribute.map", userProperties.get(propertyKey));
                    props.put(propertyName,attrs.get(propertyKey));
                }

            }
            return new JahiaUserImpl(StringUtils.substringAfterLast(attrs.get("cn").toString(), ":").trim(),null,props,false,key);
        }
    }

    private class JahiaGroupAttributesMapper implements AttributesMapper<Member> {
        @Override
        public Member mapFromAttributes(Attributes attributes) throws NamingException {
            return new Member(StringUtils.substringAfterLast(attributes.get("cn").toString(), ":").trim(),Member.MemberType.USER);
        }
    }


}

