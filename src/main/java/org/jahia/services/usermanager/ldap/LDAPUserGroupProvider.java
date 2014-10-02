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
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.users.*;
import org.jahia.services.usermanager.*;
import org.jahia.services.usermanager.ldap.cache.LDAPAbstractCacheEntry;
import org.jahia.services.usermanager.ldap.cache.LDAPCacheManager;
import org.jahia.services.usermanager.ldap.cache.LDAPGroupCacheEntry;
import org.jahia.services.usermanager.ldap.cache.LDAPUserCacheEntry;
import org.jahia.services.usermanager.ldap.config.AbstractConfig;
import org.jahia.services.usermanager.ldap.config.GroupConfig;
import org.jahia.services.usermanager.ldap.config.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.DefaultIncrementalAttributesMapper;
import org.springframework.ldap.query.ConditionCriteria;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.NamingEnumeration;
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
    private static Logger logger = LoggerFactory.getLogger(UserGroupProvider.class);

    private ExternalUserGroupService externalUserGroupService;
    private LdapTemplate ldapTemplate;
    private String key;

    // Configs
    private UserConfig userConfig;
    private GroupConfig groupConfig;

    // Cache
    private LDAPCacheManager ldapCacheManager;

    @Override
    public JahiaUser getUser(String name) throws UserNotFoundException {
        LDAPUserCacheEntry userCacheEntry = ldapCacheManager.getUserCacheEntry(getKey(), name);
        if(userCacheEntry != null){
            if(userCacheEntry.getExist() != null && !userCacheEntry.getExist()){
                throw new UserNotFoundException("unable to find user " + name + " on provider " + key);
            }else if(userCacheEntry.getUser() != null) {
                return userCacheEntry.getUser();
            }
        }

        String dn = userCacheEntry != null && StringUtils.isNotEmpty(userCacheEntry.getDn()) ? userCacheEntry.getDn() : getDnFromName(name, false);
        if(StringUtils.isNotEmpty(dn)){
            List<String> userAttrs = new ArrayList<String>(userConfig.getAttributesMapper().values());
            userAttrs.add(userConfig.getUidSearchAttribute());
            LDAPUserCacheEntry user = ldapTemplate.lookup(dn, userAttrs.toArray(new String[userAttrs.size()]),new JahiaUserAttributesMapper(userCacheEntry));
            if(user != null) {
                ldapCacheManager.cacheUser(getKey(), user);
                return user.getUser();
            }
        }

        // user not retrieve, cache it as not exist
        userCacheEntry = new LDAPUserCacheEntry(name);
        userCacheEntry.setExist(false);
        ldapCacheManager.cacheUser(getKey(), userCacheEntry);
        throw new UserNotFoundException("unable to find user " + name + " on provider " + key);
    }

    @Override
    public JahiaGroup getGroup(String name) throws GroupNotFoundException {
        LDAPGroupCacheEntry groupCacheEntry = ldapCacheManager.getGroupCacheEntry(getKey(), name);
        if(groupCacheEntry != null){
            if(groupCacheEntry.getExist() != null && !groupCacheEntry.getExist()){
                throw new GroupNotFoundException("unable to find group " + name + " on provider " + key);
            }else if(groupCacheEntry.getGroup() != null) {
                return groupCacheEntry.getGroup();
            }
        }

        String dn = groupCacheEntry != null && StringUtils.isNotEmpty(groupCacheEntry.getDn()) ? groupCacheEntry.getDn() : getDnFromName(name, true);
        if(StringUtils.isNotEmpty(dn)){
            List<String> groupAttrs = new ArrayList<String>(groupConfig.getAttributesMapper().values());
            groupAttrs.add(groupConfig.getSearchAttribute());
            LDAPGroupCacheEntry group = ldapTemplate.lookup(dn, groupAttrs.toArray(new String[groupAttrs.size()]), new JahiaGroupAttributesMapper(groupCacheEntry, groupConfig.isPreload()));
            if(group != null) {
                ldapCacheManager.cacheGroup(getKey(), group);
                return group.getGroup();
            }
        }

        // group not found, cache it as not exist
        groupCacheEntry = new LDAPGroupCacheEntry(name);
        groupCacheEntry.setExist(false);
        ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
        throw new GroupNotFoundException("unable to find group " + name + " on provider " + key);
    }

    @Override
    public List<Member> getGroupMembers(String groupName) {
        LDAPGroupCacheEntry groupCacheEntry = ldapCacheManager.getGroupCacheEntry(getKey(), groupName);
        if(groupCacheEntry != null && groupCacheEntry.getMembers() != null){
            return groupCacheEntry.getMembers();
        }

        if(groupCacheEntry == null) {
            groupCacheEntry = new LDAPGroupCacheEntry(groupName);
            groupCacheEntry.setDn(getDnFromName(groupName, true));
        }

        groupCacheEntry.setExist(true);
        groupCacheEntry.setMembers(loadMembersFromDN(groupCacheEntry.getDn()));
        ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
        return groupCacheEntry.getMembers();
    }

    private List<Member> loadMembersFromDN(String groupDN) {
        NamingEnumeration<?> members = null;
        // use AD range search if a range is specify in the conf
        if(groupConfig.getAdRangeStep() > 0){
            DefaultIncrementalAttributesMapper incrementalAttributesMapper = new DefaultIncrementalAttributesMapper(groupConfig.getAdRangeStep(),
                    groupConfig.getMembersAttribute());

            while (incrementalAttributesMapper.hasMore()){
                ldapTemplate.lookup(groupDN, incrementalAttributesMapper.getAttributesForLookup(), incrementalAttributesMapper);
            }

            Attributes attributes = incrementalAttributesMapper.getCollectedAttributes();
            try {
                members = attributes.get(groupConfig.getMembersAttribute()).getAll();
            } catch (NamingException e){
                logger.error("Error retrieving the LDAP members using range on group: " + groupDN, e);
            }
        } else {
            members = ldapTemplate.lookup(groupDN, new String[]{groupConfig.getMembersAttribute()}, new AttributesMapper<NamingEnumeration<?>>() {
                @Override
                public NamingEnumeration<?> mapFromAttributes(Attributes attributes) throws NamingException {
                    return attributes.get(groupConfig.getMembersAttribute()) != null ?
                            attributes.get(groupConfig.getMembersAttribute()).getAll() : null;
                }
            });
        }

        return loadMembers(members);
    }

    private List<Member> loadMembers(NamingEnumeration<?> members) {
        List<Member> memberList = new ArrayList<Member>();
        try {
            while (members != null && members.hasMore()){
                final String memberNaming = (String) members.next();
                Member member = ldapTemplate.lookup(memberNaming,
                        new String[] {"objectclass", userConfig.getUidSearchAttribute(), groupConfig.getSearchAttribute()},
                        new ContextMapper<Member>() {
                            @Override
                            public Member mapFromContext(Object ctx) throws NamingException {
                                DirContextAdapter ctxAdapter = (DirContextAdapter) ctx;
                                Object[] objectclass = ctxAdapter.getObjectAttributes("objectclass");
                                if(ArrayUtils.contains(objectclass, userConfig.getSearchObjectclass())){
                                    // user
                                    return new Member(ctxAdapter.getStringAttribute(userConfig.getUidSearchAttribute()), Member.MemberType.USER);
                                } else if (ArrayUtils.contains(objectclass, groupConfig.getSearchObjectclass())){
                                    // group
                                    return new Member(ctxAdapter.getStringAttribute(groupConfig.getSearchAttribute()), Member.MemberType.GROUP);
                                } else {
                                    // no objectclass mapping found for member
                                    logger.warn("No matching objectclass found on member: " + memberNaming + ", " +
                                            "valid objectclass are: " + userConfig.getSearchObjectclass() +", " + groupConfig.getSearchObjectclass() + ". " +
                                            "Tyring to resolve member regarding the attributes.");
                                    if(ctxAdapter.attributeExists(userConfig.getUidSearchAttribute())){
                                        logger.warn("Member " + memberNaming + " resolved as a user");
                                        return new Member(ctxAdapter.getStringAttribute(userConfig.getUidSearchAttribute()), Member.MemberType.USER);
                                    } else if (ctxAdapter.attributeExists(groupConfig.getSearchAttribute())){
                                        logger.warn("Member " + memberNaming + " resolved as a group");
                                        return new Member(ctxAdapter.getStringAttribute(groupConfig.getSearchAttribute()), Member.MemberType.GROUP);
                                    } else {
                                        logger.warn("Member " + memberNaming + " not returned, because not resolved");
                                    }
                                }
                                return null;
                            }
                        });
                if(member != null) {
                    memberList.add(member);
                }
            }
        } catch (NamingException e) {
            logger.error("Error retrieving LDAP group members for group", e);
        }

        return memberList;
    }

    @Override
    public List<String> getMembership(Member member) {
        boolean isGroup = member.getType().equals(Member.MemberType.GROUP);
        LDAPAbstractCacheEntry cacheEntry = isGroup ? ldapCacheManager.getGroupCacheEntry(getKey(), member.getName())
                : ldapCacheManager.getUserCacheEntry(getKey(), member.getName());
        if(cacheEntry != null && cacheEntry.getMemberships() != null){
            return cacheEntry.getMemberships();
        }

        if(cacheEntry == null){
            cacheEntry = isGroup ? new LDAPGroupCacheEntry(member.getName()) : new LDAPUserCacheEntry(member.getName());
            cacheEntry.setDn(getDnFromName(member.getName(), isGroup));
        }

        List<String> memberships = ldapTemplate.search(
                query().base(groupConfig.getSearchName())
                        .where("objectclass")
                        .is(groupConfig.getSearchObjectclass())
                        .and(groupConfig.getMembersAttribute())
                        .like(cacheEntry.getDn()),
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get(groupConfig.getSearchAttribute()).get().toString();
                    }
                });

        cacheEntry.setMemberships(memberships);
        if(isGroup){
            ldapCacheManager.cacheGroup(getKey(), (LDAPGroupCacheEntry) cacheEntry);
        } else {
            ldapCacheManager.cacheUser(getKey(), (LDAPUserCacheEntry) cacheEntry);
        }
        return cacheEntry.getMemberships();
    }

    @Override
    public List<String> searchUsers(Properties searchCriteria) {
        ContainerCriteria query = buildQuery(searchCriteria, true);
        return ldapTemplate.search(query,
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get(userConfig.getUidSearchAttribute()).get().toString();
                    }
                });
    }

    @Override
    public List<String> searchGroups(Properties searchCriteria) {
        ContainerCriteria query = buildQuery(searchCriteria, false);
        List<String> groups = ldapTemplate.search(query,
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get(groupConfig.getSearchAttribute()).get().toString();
                    }
                });
        return groups;
    }

    @Override
    public boolean verifyPassword(String userName, String userPassword) {
        DirContext ctx = null;
        try {
            String userDn = getDnFromName(userName, false);
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

    private String getDnFromName(String name, boolean isGroup) {
        List<String> results = ldapTemplate.search(
                query().base(isGroup?groupConfig.getSearchName():userConfig.getUidSearchName())
                        .where("objectclass")
                        .is(isGroup?groupConfig.getSearchObjectclass():userConfig.getSearchObjectclass())
                        .and(isGroup?groupConfig.getSearchAttribute():userConfig.getUidSearchAttribute())
                        .is(name), new ContextMapper<String>() {
            @Override
            public String mapFromContext(Object ctx) throws NamingException {
                return ((LdapCtx) ctx).getNameInNamespace();
            }
        });

        if (results == null || results.size() == 0) {
            return null;
        }
        else if (results.size() != 1) {
            String object = isGroup ? "group" : "user";
            logger.warn("More than one LDAP " + object + " is retrieve with the attribute " +
                    (isGroup ? groupConfig.getSearchAttribute() : userConfig.getUidSearchAttribute()) + ":");
            for (String result : results){
                logger.warn("LDAP " + object + " :" + result);
            }
        }
        return results.get(0);
    }

    private class JahiaUserAttributesMapper implements ContextMapper<LDAPUserCacheEntry> {
        private LDAPUserCacheEntry userCacheEntry;

        private JahiaUserAttributesMapper(LDAPUserCacheEntry userCacheEntry) {
            this.userCacheEntry = userCacheEntry;
        }

        @Override
        public LDAPUserCacheEntry mapFromContext(Object ctx) throws NamingException {
            DirContextAdapter ctxAdapter = (DirContextAdapter) ctx;
            Attributes attrs = ctxAdapter.getAttributes();
            Properties props = new Properties();
            for (String propertyKey : userConfig.getAttributesMapper().keySet()) {
                Attribute ldapAttribute = attrs.get(userConfig.getAttributesMapper().get(propertyKey));
                if (ldapAttribute != null && ldapAttribute.get() instanceof String) {
                    props.put(propertyKey, ldapAttribute.get());
                }
            }
            String userId = (String) attrs.get(userConfig.getUidSearchAttribute()).get();
            JahiaUser jahiaUser = new JahiaUserImpl(userId, null, props, false, key);
            if(userCacheEntry == null) {
                userCacheEntry = new LDAPUserCacheEntry(userId);
            }
            userCacheEntry.setDn(ctxAdapter.getNameInNamespace());
            userCacheEntry.setExist(true);
            userCacheEntry.setUser(jahiaUser);

            return userCacheEntry;
        }
    }

    private class JahiaGroupAttributesMapper implements ContextMapper<LDAPGroupCacheEntry> {
        private LDAPGroupCacheEntry groupCacheEntry;
        private boolean loadMembers;

        private JahiaGroupAttributesMapper(LDAPGroupCacheEntry groupCacheEntry, boolean loadMembers) {
            this.groupCacheEntry = groupCacheEntry;
            this.loadMembers = loadMembers;
        }

        @Override
        public LDAPGroupCacheEntry mapFromContext(Object ctx) throws NamingException {
            DirContextAdapter ctxAdapter = (DirContextAdapter) ctx;
            Attributes attrs = ctxAdapter.getAttributes();
            Properties props = new Properties();
            for (String propertyKey : groupConfig.getAttributesMapper().keySet()) {
                Attribute ldapAttribute = attrs.get(groupConfig.getAttributesMapper().get(propertyKey));
                if (ldapAttribute != null && ldapAttribute.get() instanceof String) {
                    props.put(propertyKey, ldapAttribute.get());
                }
            }
            String groupId = (String) attrs.get(groupConfig.getSearchAttribute()).get();
            JahiaGroup jahiaGroup = new  JahiaGroupImpl(groupId, null, null, props);

            if(groupCacheEntry == null) {
                groupCacheEntry = new LDAPGroupCacheEntry(jahiaGroup.getName());
            }
            groupCacheEntry.setDn(ctxAdapter.getNameInNamespace());
            groupCacheEntry.setExist(true);
            groupCacheEntry.setGroup(jahiaGroup);
            if(loadMembers){
                groupCacheEntry.setMembers(loadMembersFromDN(groupCacheEntry.getDn()));
            }

            return groupCacheEntry;
        }
    }

    private ContainerCriteria buildQuery(Properties searchCriteria, boolean isUser){
        AbstractConfig config = isUser ? userConfig : groupConfig;
        ContainerCriteria query = query().base(isUser ? userConfig.getUidSearchName() : groupConfig.getSearchName())
                .countLimit((int) config.getSearchCountlimit())
                .where("objectclass").is(StringUtils.defaultString(config.getSearchObjectclass(), "*"));

        // transform jnt:user props to ldap props
        Properties ldapfilters = mapJahiaPropertiesToLDAP(searchCriteria, config.getAttributesMapper());

        // define and / or operator
        boolean orOp = true;
        if (ldapfilters.size() > 1) {
            if (searchCriteria.containsKey(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)) {
                if (((String) searchCriteria.get(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)).trim().toLowerCase().equals("and")) {
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

    public String getKey() {
        return key;
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

    public void setLdapCacheManager(LDAPCacheManager ldapCacheManager) {
        this.ldapCacheManager = ldapCacheManager;
    }

    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
    }

    public void setGroupConfig(GroupConfig groupConfig) {
        this.groupConfig = groupConfig;
    }
}

