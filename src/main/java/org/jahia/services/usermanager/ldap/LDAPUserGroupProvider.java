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
import org.springframework.ldap.core.*;
import org.springframework.ldap.core.support.DefaultIncrementalAttributesMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.ConditionCriteria;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.ldap.query.SearchScope;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.*;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * Created by david on 16/09/14.
 * Implementation of UserGroupProvider for Spring LDAP
 */
public class LDAPUserGroupProvider implements UserGroupProvider {
    private static Logger logger = LoggerFactory.getLogger(UserGroupProvider.class);

    private ExternalUserGroupService externalUserGroupService;
    private LdapContextSource contextSource;
    private LdapTemplate ldapTemplate;
    private String key;

    // Configs
    private UserConfig userConfig;
    private GroupConfig groupConfig;
    // if user and group are different
    private boolean distinctBase = false;

    // Cache
    private LDAPCacheManager ldapCacheManager;

    @Override
    public JahiaUser getUser(String name) throws UserNotFoundException {
        LDAPUserCacheEntry userCacheEntry = getUserCacheEntry(name, true);
        if(!userCacheEntry.getExist()){
            throw new UserNotFoundException("unable to find user " + name + " on provider " + key);
        } else {
            return userCacheEntry.getUser();
        }
    }

    @Override
    public JahiaGroup getGroup(String name) throws GroupNotFoundException {
        LDAPGroupCacheEntry groupCacheEntry = getGroupCacheEntry(name, true);
        if(!groupCacheEntry.getExist()){
            throw new GroupNotFoundException("unable to find group " + name + " on provider " + key);
        } else {
            return groupCacheEntry.getGroup();
        }
    }

    @Override
    public List<Member> getGroupMembers(String groupName) {
        LDAPGroupCacheEntry groupCacheEntry = getGroupCacheEntry(groupName, false);
        if(!groupCacheEntry.getExist()){
            return Collections.emptyList();
        }
        if(groupCacheEntry.getMembers() != null){
            return groupCacheEntry.getMembers();
        }

        groupCacheEntry.setMembers(loadMembersFromDN(groupCacheEntry.getDn()));
        ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
        return groupCacheEntry.getMembers();
    }

    @Override
    public List<String> getMembership(Member member) {
        boolean isGroup = member.getType().equals(Member.MemberType.GROUP);
        LDAPAbstractCacheEntry cacheEntry = isGroup ? getGroupCacheEntry(member.getName(), false)
                : getUserCacheEntry(member.getName(), false);
        if(cacheEntry.getMemberships() != null){
            return cacheEntry.getMemberships();
        }

        List<String> memberships = ldapTemplate.search(
                query().base(groupConfig.getSearchName())
                        .attributes(groupConfig.getSearchAttribute())
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
        SearchNameClassPairCallbackHandler searchNameClassPairCallbackHandler = new SearchNameClassPairCallbackHandler(true);
        ldapTemplate.search(query, searchNameClassPairCallbackHandler);
        return searchNameClassPairCallbackHandler.getNames();
    }

    @Override
    public List<String> searchGroups(Properties searchCriteria) {
        ContainerCriteria query = buildQuery(searchCriteria, false);
        SearchNameClassPairCallbackHandler searchNameClassPairCallbackHandler = new SearchNameClassPairCallbackHandler(false);
        ldapTemplate.search(query, searchNameClassPairCallbackHandler);
        return searchNameClassPairCallbackHandler.getNames();
    }

    @Override
    public boolean verifyPassword(String userName, String userPassword) {
        DirContext ctx = null;
        try {
            LDAPUserCacheEntry userCacheEntry = getUserCacheEntry(userName, true);
            if(userCacheEntry.getExist()){
                ctx = contextSource.getContext(userCacheEntry.getDn(), userPassword);
                // Take care here - if a base was specified on the ContextSource
                // that needs to be removed from the user DN for the lookup to succeed.
                ctx.lookup(userCacheEntry.getDn());
                return true;
            }
        } catch (Exception e) {
            // Context creation failed - authentication did not succeed
            //logger.error("Login failed", e);
        } finally {
            // It is imperative that the created DirContext instance is always closed
            LdapUtils.closeContext(ctx);
        }
        return false;
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
                final LdapName memberLdapName = LdapUtils.newLdapName(memberNaming);
                // try to know if we deal with a group or a user
                Boolean isUser = null;
                if (memberLdapName.startsWith(new LdapName(userConfig.getUidSearchName()))) {
                    // it's a user
                    isUser = distinctBase ? true : null;
                } else if (memberLdapName.startsWith(new LdapName(groupConfig.getSearchName()))) {
                    // it's a group
                    isUser = distinctBase ? false : null;
                } else {
                    // it's not in the scope
                    continue;
                }

                // try to retrieve the object from the cache
                LDAPAbstractCacheEntry cacheEntry;
                if (isUser != null){
                    if(isUser) {
                        cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), memberNaming);
                    } else {
                        cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), memberNaming);
                    }
                } else {
                    // look in all cache
                    cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), memberNaming);
                    if(cacheEntry == null) {
                        cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), memberNaming);
                        isUser = cacheEntry != null ? false : null;
                    } else {
                        isUser = true;
                    }
                }
                if(cacheEntry != null) {
                    if(isUser) {
                        memberList.add(new Member(cacheEntry.getName(), Member.MemberType.USER));
                    } else {
                        memberList.add(new Member(cacheEntry.getName(), Member.MemberType.GROUP));
                    }
                    continue;
                }

                // try to retrieve
                if(isUser != null && userConfig.isSearchAttributeInDn()){
                    String name = getNameFromDn(memberNaming, isUser);
                    if (StringUtils.isNotEmpty(name)){
                        memberList.add(isUser ? new Member(name, Member.MemberType.USER) : new Member(name, Member.MemberType.GROUP));
                        continue;
                    }
                }

                // do queries
                // and cache the result
                Member member = null;
                List<String> userAttrs = new ArrayList<String>(userConfig.getAttributesMapper().values());
                userAttrs.add(userConfig.getUidSearchAttribute());
                CacheEntryNameClassPairCallbackHandler nameClassPairCallbackHandler = new CacheEntryNameClassPairCallbackHandler(null, true);
                ldapTemplate.search(query().base(memberNaming)
                        .attributes(userAttrs.toArray(new String[userAttrs.size()]))
                        .searchScope(SearchScope.OBJECT)
                        .where("objectclass").is(userConfig.getSearchObjectclass()),
                        nameClassPairCallbackHandler);

                if(nameClassPairCallbackHandler.getCacheEntry() == null) {
                    List<String> groupAttrs = new ArrayList<String>(groupConfig.getAttributesMapper().values());
                    groupAttrs.add(groupConfig.getSearchAttribute());
                    nameClassPairCallbackHandler = new CacheEntryNameClassPairCallbackHandler(null, false);
                    ldapTemplate.search(query().base(memberNaming)
                            .attributes(groupAttrs.toArray(new String[groupAttrs.size()]))
                            .searchScope(SearchScope.OBJECT)
                            .where("objectclass").is(groupConfig.getSearchObjectclass()),
                            nameClassPairCallbackHandler);
                    if(nameClassPairCallbackHandler.getCacheEntry() != null) {
                        LDAPGroupCacheEntry ldapGroupCacheEntry = (LDAPGroupCacheEntry) nameClassPairCallbackHandler.getCacheEntry();
                        ldapGroupCacheEntry.setDn(memberNaming);
                        member = new Member(ldapGroupCacheEntry.getName(), Member.MemberType.GROUP);
                        ldapCacheManager.cacheGroup(getKey(), ldapGroupCacheEntry);
                    }
                } else {
                    LDAPUserCacheEntry ldapUserCacheEntry = (LDAPUserCacheEntry) nameClassPairCallbackHandler.getCacheEntry();
                    ldapUserCacheEntry.setDn(memberNaming);
                    member = new Member(ldapUserCacheEntry.getName(), Member.MemberType.USER);
                    ldapCacheManager.cacheUser(getKey(), ldapUserCacheEntry);
                }

                if(member != null) {
                    memberList.add(member);
                }
            }
        } catch (NamingException e) {
            logger.error("Error retrieving LDAP group members for group", e);
        }

        return memberList;
    }

    private LDAPUserCacheEntry getUserCacheEntry(String userName, boolean cache){
        LDAPUserCacheEntry userCacheEntry = ldapCacheManager.getUserCacheEntryByName(getKey(), userName);
        if(userCacheEntry != null){
            if(userCacheEntry.getExist() != null && userCacheEntry.getExist() && userCacheEntry.getUser() != null){
                return userCacheEntry;
            } else if(userCacheEntry.getExist() != null && !userCacheEntry.getExist()){
                return userCacheEntry;
            }
        }

        List<String> userAttrs = new ArrayList<String>(userConfig.getAttributesMapper().values());
        userAttrs.add(userConfig.getUidSearchAttribute());
        CacheEntryNameClassPairCallbackHandler nameClassPairCallbackHandler = new CacheEntryNameClassPairCallbackHandler(userCacheEntry, true);
        ldapTemplate.search(query().base(userConfig.getUidSearchName())
                .attributes(userAttrs.toArray(new String[userAttrs.size()]))
                .where("objectclass").is(userConfig.getSearchObjectclass())
                .and(userConfig.getUidSearchAttribute()).is(userName),
                nameClassPairCallbackHandler);

        if(nameClassPairCallbackHandler.getCacheEntry() != null){
            userCacheEntry = (LDAPUserCacheEntry) nameClassPairCallbackHandler.getCacheEntry();
            userCacheEntry.setExist(true);
            return userCacheEntry;
        } else {
            userCacheEntry = new LDAPUserCacheEntry(userName);
            userCacheEntry.setExist(false);
        }

        if(cache){
            ldapCacheManager.cacheUser(getKey(), userCacheEntry);
        }

        return userCacheEntry;
    }

    private LDAPGroupCacheEntry getGroupCacheEntry(String groupName, boolean cache){
        LDAPGroupCacheEntry groupCacheEntry = ldapCacheManager.getGroupCacheEntryName(getKey(), groupName);
        if(groupCacheEntry != null){
            if(groupCacheEntry.getExist() != null && groupCacheEntry.getExist() && groupCacheEntry.getGroup() != null){
                return groupCacheEntry;
            } else if(groupCacheEntry.getExist() != null && !groupCacheEntry.getExist()){
                return groupCacheEntry;
            }
        }

        List<String> groupAttrs = new ArrayList<String>(groupConfig.getAttributesMapper().values());
        groupAttrs.add(groupConfig.getSearchAttribute());
        CacheEntryNameClassPairCallbackHandler nameClassPairCallbackHandler = new CacheEntryNameClassPairCallbackHandler(groupCacheEntry, false);
        ldapTemplate.search(query().base(groupConfig.getSearchName())
                .attributes(groupAttrs.toArray(new String[groupAttrs.size()]))
                .where("objectclass").is(groupConfig.getSearchObjectclass())
                .and(groupConfig.getSearchAttribute()).is(groupName),
                nameClassPairCallbackHandler);

        if(nameClassPairCallbackHandler.getCacheEntry() != null){
            groupCacheEntry = (LDAPGroupCacheEntry) nameClassPairCallbackHandler.getCacheEntry();
            groupCacheEntry.setExist(true);
            return groupCacheEntry;
        } else {
            groupCacheEntry = new LDAPGroupCacheEntry(groupName);
            groupCacheEntry.setExist(false);
        }

        if(cache){
            ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
        }

        return groupCacheEntry;
    }

    private String getNameFromDn(String dn, boolean isUser) {
        LdapName ln = LdapUtils.newLdapName(dn);
        for (Rdn rdn : ln.getRdns()) {
            if (rdn.getType().equalsIgnoreCase(isUser ? userConfig.getUidSearchAttribute() : groupConfig.getSearchAttribute())) {
                return rdn.getValue().toString();
            }
        }

        return null;
    }

    private class CacheEntryNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        private LDAPAbstractCacheEntry cacheEntry;
        private boolean isUser;

        public LDAPAbstractCacheEntry getCacheEntry() {
            return cacheEntry;
        }

        private CacheEntryNameClassPairCallbackHandler(LDAPAbstractCacheEntry cacheEntry, boolean isUser) {
            this.cacheEntry = cacheEntry;
            this.isUser = isUser;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            SearchResult searchResult = (SearchResult) nameClassPair;
            if(isUser) {
                cacheEntry = attributesToUserCacheEntry(searchResult.getAttributes(), (LDAPUserCacheEntry) cacheEntry);
            } else {
                cacheEntry = attributesToGroupCacheEntry(searchResult.getAttributes(), (LDAPGroupCacheEntry) cacheEntry);
            }
            cacheEntry.setDn(searchResult.getNameInNamespace());
        }
    }

    private class SearchNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        private List<String> names = new ArrayList<String>();
        private boolean isUser;

        public List<String> getNames() {
            return names;
        }

        private SearchNameClassPairCallbackHandler(boolean isUser) {
            this.isUser = isUser;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            SearchResult searchResult = (SearchResult) nameClassPair;
            if(isUser) {
                LDAPUserCacheEntry cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                CacheEntryNameClassPairCallbackHandler nameClassPairCallbackHandler = new CacheEntryNameClassPairCallbackHandler(cacheEntry, true);
                nameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
                LDAPUserCacheEntry ldapUserCacheEntry = (LDAPUserCacheEntry) nameClassPairCallbackHandler.getCacheEntry();
                ldapCacheManager.cacheUser(getKey(), ldapUserCacheEntry);
                names.add(ldapUserCacheEntry.getName());
            } else {
                LDAPGroupCacheEntry cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                CacheEntryNameClassPairCallbackHandler nameClassPairCallbackHandler = new CacheEntryNameClassPairCallbackHandler(cacheEntry, false);
                nameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
                LDAPGroupCacheEntry ldapGroupCacheEntry = (LDAPGroupCacheEntry) nameClassPairCallbackHandler.getCacheEntry();
                ldapCacheManager.cacheGroup(getKey(), ldapGroupCacheEntry);
                names.add(ldapGroupCacheEntry.getName());
            }
        }
    }



    private LDAPUserCacheEntry attributesToUserCacheEntry(Attributes attrs, LDAPUserCacheEntry userCacheEntry) throws NamingException {
        String userId = (String) attrs.get(userConfig.getUidSearchAttribute()).get();
        JahiaUser jahiaUser = new JahiaUserImpl(userId, null, attributesToJahiaProperties(attrs, true), false, key);
        if(userCacheEntry == null) {
            userCacheEntry = new LDAPUserCacheEntry(userId);
        }
        userCacheEntry.setExist(true);
        userCacheEntry.setUser(jahiaUser);

        return userCacheEntry;
    }

    private LDAPGroupCacheEntry attributesToGroupCacheEntry(Attributes attrs, LDAPGroupCacheEntry groupCacheEntry) throws NamingException {
        String groupId = (String) attrs.get(groupConfig.getSearchAttribute()).get();
        JahiaGroup jahiaGroup = new  JahiaGroupImpl(groupId, null, null, attributesToJahiaProperties(attrs, false));

        if(groupCacheEntry == null) {
            groupCacheEntry = new LDAPGroupCacheEntry(jahiaGroup.getName());
        }
        groupCacheEntry.setExist(true);
        groupCacheEntry.setGroup(jahiaGroup);

        return groupCacheEntry;
    }

    private Properties attributesToJahiaProperties(Attributes attributes, boolean isUser) {
        Properties props = new Properties();
        Map<String, String> attributesMapper = isUser ? userConfig.getAttributesMapper() : groupConfig.getAttributesMapper();
        for (String propertyKey : attributesMapper.keySet()) {
            Attribute ldapAttribute = attributes.get(attributesMapper.get(propertyKey));
            try {
                if (ldapAttribute != null && ldapAttribute.get() instanceof String) {
                    props.put(propertyKey, ldapAttribute.get());
                }
            }catch (NamingException e) {
                logger.error("Error reading LDAP attribute:" + ldapAttribute.toString());
            }

        }
        return props;
    }

    private ContainerCriteria buildQuery(Properties searchCriteria, boolean isUser){
        AbstractConfig config = isUser ? userConfig : groupConfig;

        List<String> attributesToRetrieve = new ArrayList<String>();
        if(isUser){
            attributesToRetrieve.addAll(userConfig.getAttributesMapper().values());
            attributesToRetrieve.add(userConfig.getUidSearchAttribute());
        } else {
            attributesToRetrieve.addAll(groupConfig.getAttributesMapper().values());
            attributesToRetrieve.add(groupConfig.getSearchAttribute());
        }

        ContainerCriteria query = query().base(isUser ? userConfig.getUidSearchName() : groupConfig.getSearchName())
                .attributes(attributesToRetrieve.toArray(new String[attributesToRetrieve.size()]))
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

    public void setContextSource(LdapContextSource contextSource) {
        this.contextSource = contextSource;
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

    public void setDistinctBase(boolean distinctBase) {
        this.distinctBase = distinctBase;
    }
}

