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

import com.sun.jndi.ldap.LdapCtx;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahia.modules.external.users.Member;
import org.jahia.modules.external.users.UserGroupProvider;
import org.jahia.modules.external.users.UserNotFoundException;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserImpl;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.services.usermanager.ldap.config.AbstractConfig;
import org.jahia.services.usermanager.ldap.config.GroupConfig;
import org.jahia.services.usermanager.ldap.config.UserConfig;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.ConditionCriteria;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.util.*;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * Created by david on 16/09/14.
 * Implementation of UserGroupProvider for Spring LDAP
 */
public class LDAPUserGroupProvider implements UserGroupProvider {

    // the LDAP User cache name.
    public static final String LDAP_USER_CACHE = "LDAPUsersCache";
    public static final String LDAP_NON_EXISTANT_USER_CACHE = "LDAPNonExistantUsersCache";
    // the LDAP Group cache name.
    public static final String LDAP_GROUP_CACHE = "LDAPGroupsCache";
    public static final String LDAP_NONEXISTANT_GROUP_CACHE = "LDAPNonExistantGroupsCache";

    private ExternalUserGroupService externalUserGroupService;
    private LdapTemplate ldapTemplate;
    private String key;
    private List<String> groupCache = new ArrayList<String>();

    // Configs
    private UserConfig userConfig;
    private GroupConfig groupConfig;

    // todo : handle properly caches
    private Map<String,List<Member>> groupMembersCache = new HashMap<String, List<Member>>();
    private Map<Properties,List<String>> groupsCache = new HashMap<Properties, List<String>>();

    @Override
    public JahiaUser getUser(String name) throws UserNotFoundException {
        List<JahiaUser> users = ldapTemplate.search(query()
                .base(userConfig.getUidSearchName())
                .where(userConfig.getUidSearchAttribute())
                .is(name), new JahiaUserAttributesMapper());
        if (users.isEmpty()) {
            throw new UserNotFoundException("unable to find user " + name + " on provider " + key);
        }
        return users.get(0);
    }

    @Override
    public boolean groupExists(String name) {
        if (groupCache != null && groupCache.contains(name)) {
            return true;
        } else {
            if (!ldapTemplate.search(
                    query().base(groupConfig.getSearchName()).where("objectclass").is("groupOfUniqueNames").and("cn").is(name),
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
                query().base(groupConfig.getSearchName()).where("objectclass").is("groupOfUniqueNames").and("cn").is(groupName),
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
        ContainerCriteria query = buildQuery(searchCriterias, true);
        return ldapTemplate.search(query,
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get(userConfig.getUidSearchAttribute()).get().toString();
                    }
                });
    }

    @Override
    public List<String> searchGroups(Properties searchCriterias) {
        if (groupsCache.containsKey(searchCriterias)) {
            return groupsCache.get(searchCriterias);
        }
        ContainerCriteria query = buildQuery(searchCriterias, false);
        List<String> groups = ldapTemplate.search(query,
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get(groupConfig.getSearchAttribute()).get().toString();
                    }
                });
        groupsCache.put(searchCriterias,groups);
        return groups;
    }

    @Override
    public boolean verifyPassword(String userName, String userPassword) {
        DirContext ctx = null;
        try {
            String userDn = getUserDnFromName(userName);
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

    private String getUserDnFromName(String name) {
        return ldapTemplate.searchForObject(
                query().base(userConfig.getUidSearchName())
                        .where("objectclass")
                        .is(userConfig.getSearchObjectclass())
                        .and(userConfig.getUidSearchAttribute())
                        .is(name), new ContextMapper<String>() {
            @Override
            public String mapFromContext(Object ctx) throws NamingException {
                return ((LdapCtx) ctx).getNameInNamespace();
            }
        });
    }

    private class JahiaUserAttributesMapper implements AttributesMapper<JahiaUser> {
        public JahiaUser mapFromAttributes(Attributes attrs) throws NamingException {
            Properties props = new Properties();
            for (String propertyKey : userConfig.getAttributesMapper().keySet()) {
                Attribute ldapAttribute = attrs.get(userConfig.getAttributesMapper().get(propertyKey));
                if (ldapAttribute != null && ldapAttribute.get() instanceof String) {
                    props.put(propertyKey, ldapAttribute.get());
                }
            }
            String userId = (String) attrs.get(userConfig.getUidSearchAttribute()).get();
            return new JahiaUserImpl(userId, null, props, false, key);
        }
    }

    private ContainerCriteria buildQuery(Properties searchCriterias, boolean isUser){
        AbstractConfig config = isUser ? userConfig : groupConfig;
        ContainerCriteria query = query().base(isUser ? userConfig.getUidSearchName() : groupConfig.getSearchName())
                .where("objectclass").is(StringUtils.defaultString(config.getSearchObjectclass(), "*"));

        // transform jnt:user props to ldap props
        Properties ldapfilters = mapJahiaPropertiesToLDAP(searchCriterias, config.getAttributesMapper());

        // define and / or operator
        boolean orOp = true;
        if (ldapfilters.size() > 1) {
            if (searchCriterias.containsKey(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)) {
                if (((String) searchCriterias.get(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)).trim().toLowerCase().equals("and")) {
                    orOp = false;
                }
            }
        }

        // process the user specific filters
        ContainerCriteria filterQuery = null;
        if (ldapfilters.containsKey("*")){
            // Search on all wildcards attributes
            String filterValue = ldapfilters.getProperty("*");
            if (CollectionUtils.isNotEmpty(config.getSearchWildcardsAttributes())) {
                for (String wildcardAttribute : config.getSearchWildcardsAttributes()) {
                    if(filterQuery == null){
                        filterQuery = query().where(wildcardAttribute).like(filterValue);
                    } else {
                        addCriteriaToQuery(filterQuery, true, wildcardAttribute).like(filterValue);
                    }
                }
            }
        }else {
            // consider the attributes
            Iterator<?> filterKeys = ldapfilters.keySet().iterator();
            while (filterKeys.hasNext()) {
                String filterName = (String) filterKeys.next();
                String filterValue = ldapfilters.getProperty(filterName);

                if (filterQuery == null){
                    filterQuery = query().where(filterName).like(filterValue);
                } else {
                    addCriteriaToQuery(filterQuery, orOp, filterName).like(filterValue);
                }
            }
        }

        if(filterQuery != null){
            query.and(filterQuery);
        }

        return query;
    }

    private ConditionCriteria addCriteriaToQuery(ContainerCriteria query, boolean isOr, String attribute){
        if (isOr){
            return query.or(attribute);
        } else {
            return query.and(attribute);
        }
    }

    private Properties mapJahiaPropertiesToLDAP(Properties searchCriteria, Map<String, String> configProperties) {
        if (searchCriteria.size() == 0) {
            return searchCriteria;
        }
        Properties p = new Properties();
        if (searchCriteria.containsKey("*")) {
            p.setProperty("*", searchCriteria.getProperty("*"));
            if (searchCriteria.size() == 1) {
                return p;
            }
        }

        for (Map.Entry<String, String> property : configProperties.entrySet()) {
            if (StringUtils.isNotEmpty(property.getKey()) && searchCriteria.get(property.getKey()) != null) {
                p.setProperty(property.getValue(), (String) searchCriteria.get(property.getKey()));
            }
        }

        return p;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setExternalUserGroupService(ExternalUserGroupService externalUserGroupService) {
        this.externalUserGroupService = externalUserGroupService;
    }

    /**
     * defines the LdapTemplate for this provider
     * @param ldapTemplate
     */
    public void setLdapTemplate(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
        groupMembersCache.clear();
        groupsCache.clear();
        groupCache.clear();
    }

    /**
     * register the provider
     */
    public void register() {
        externalUserGroupService.register(key,this);
    }

    /**
     * unregister the provider
     */
    public void unregister() {
        externalUserGroupService.unregister(key);
    }

    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
    }

    public void setGroupConfig(GroupConfig groupConfig) {
        this.groupConfig = groupConfig;
    }
}

