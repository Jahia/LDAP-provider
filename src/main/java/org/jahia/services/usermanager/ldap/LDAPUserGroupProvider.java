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

import com.google.common.collect.Lists;
import com.sun.jndi.ldap.LdapURL;
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

import javax.naming.InvalidNameException;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.*;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * Created by david on 16/09/14.
 * Implementation of UserGroupProvider for Spring LDAP
 */
public class LDAPUserGroupProvider implements UserGroupProvider {
    protected static final String OBJECTCLASS_ATTRIBUTE = "objectclass";
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

        List<Member> members = null;
        if(groupCacheEntry.isDynamic() && StringUtils.isNotEmpty(groupCacheEntry.getDynamicMembersURL())){
            List<Member> dynMembers = loadMembersFromUrl(groupCacheEntry.getDynamicMembersURL());
            if(CollectionUtils.isNotEmpty(dynMembers)) {
                members = Lists.newArrayList(dynMembers);
            }
        } else {
            members = loadMembersFromDN(groupCacheEntry.getDn());
        }

        if(CollectionUtils.isNotEmpty(members)){
            groupCacheEntry.setMembers(members);
            ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
            return groupCacheEntry.getMembers();
        } else {
            return Collections.emptyList();
        }
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
                        .where(OBJECTCLASS_ATTRIBUTE)
                        .is(groupConfig.getSearchObjectclass())
                        .and(groupConfig.getMembersAttribute())
                        .like(cacheEntry.getDn()),
                new AttributesMapper<String>() {
                    public String mapFromAttributes(Attributes attrs)
                            throws NamingException {
                        return attrs.get(groupConfig.getSearchAttribute()).get().toString();
                    }
                });

        if(groupConfig.isDynamicEnabled()) {
            Properties searchCriteria = new Properties();
            searchCriteria.put("*", "*");
            List<String> dynGroups = searchGroups(searchCriteria, true);
            for (String dynGroup : dynGroups) {
                List<Member> members = getGroupMembers(dynGroup);
                if(members.contains(member)){
                    memberships.add(dynGroup);
                }
            }
        }

        cacheEntry.setMemberships(memberships);
        if(isGroup){
            ldapCacheManager.cacheGroup(getKey(), (LDAPGroupCacheEntry) cacheEntry);
        } else {
            ldapCacheManager.cacheUser(getKey(), (LDAPUserCacheEntry) cacheEntry);
        }
        return cacheEntry.getMemberships();
    }

    @Override
    public List<String> searchUsers(Properties searchCriteria, long offset, long limit) {
        ContainerCriteria query = buildUserQuery(searchCriteria);
        UsersNameClassPairCallbackHandler searchNameClassPairCallbackHandler = new UsersNameClassPairCallbackHandler();
        ldapTemplate.search(query, searchNameClassPairCallbackHandler);
        ArrayList<String> l = new ArrayList<String>(searchNameClassPairCallbackHandler.getNames());
        return l.subList(Math.min((int) offset, l.size()), limit < 0 ? l.size() : Math.min((int) (offset + limit), l.size()));
    }

    @Override
    public List<String> searchGroups(Properties searchCriteria, long offset, long limit) {
        List<String> groups = new LinkedList<String>(searchGroups(searchCriteria, false));

        // handle dynamics
        if(groupConfig.isDynamicEnabled()) {
            groups.addAll(searchGroups(searchCriteria, true));
        }

        return groups.subList(Math.min((int) offset, groups.size()), limit < 0 ? groups.size() : Math.min((int) (offset + limit), groups.size()));
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

    private List<String> searchGroups(Properties searchCriteria, boolean isDynamics) {
        List<String> groups = new LinkedList<String>();
        ContainerCriteria query = buildGroupQuery(searchCriteria, isDynamics);
        GroupsNameClassPairCallbackHandler searchNameClassPairCallbackHandler = new GroupsNameClassPairCallbackHandler(isDynamics);
        ldapTemplate.search(query, searchNameClassPairCallbackHandler);
        groups.addAll(searchNameClassPairCallbackHandler.getNames());
        return groups;
    }

    /**
     * get the members from a ldap URL used for dynamic groups
     * @param url
     * @return
     */
    private List<Member> loadMembersFromUrl(String url) {
        try {
            LdapURL ldapURL = new LdapURL(url);
            DynMembersNameClassPairCallbackHandler nameClassPairCallbackHandler = new DynMembersNameClassPairCallbackHandler();

            Set<String> attrs = new HashSet<String>(getUserAttributes());
            attrs.addAll(getGroupAttributes(true));
            if (groupConfig.isDynamicEnabled()) {
                attrs.add(groupConfig.getDynamicSearchObjectclass());
            }
            attrs.add(OBJECTCLASS_ATTRIBUTE);

            SearchScope searchScope;
            if ("one".equalsIgnoreCase(ldapURL.getScope())) {
                searchScope = SearchScope.ONELEVEL;
            } else if ("base".equalsIgnoreCase(ldapURL.getScope())) {
                searchScope = SearchScope.OBJECT;
            } else {
                searchScope = SearchScope.SUBTREE;
            }

            ldapTemplate.search(query()
                    .base(ldapURL.getDN())
                    .attributes(attrs.toArray(new String[attrs.size()]))
                    .searchScope(searchScope)
                    .filter(ldapURL.getFilter()),
                    nameClassPairCallbackHandler);
            return new ArrayList<Member>(nameClassPairCallbackHandler.getMembers());
        } catch (NamingException e) {
            logger.error("Error trying to get dynamic members from url: " + url);
        }
        return null;
    }

    /**
     * get the members from a group DN
     * @param groupDN
     * @return
     */
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
                // try to know if we deal with a group or a user
                Boolean isUser = guessUserOrGroupFromDN(memberNaming);

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
                LDAPUserCacheEntry userCacheEntry = getUserCacheEntryByDN(memberNaming, true);
                if(userCacheEntry == null) {
                    // look in groups
                    LDAPGroupCacheEntry groupCacheEntry = getGroupCacheEntryByDN(memberNaming, true, false);
                    if(groupCacheEntry == null) {
                        if(groupConfig.isDynamicEnabled()){
                            // look in dynamic groups
                            groupCacheEntry = getGroupCacheEntryByDN(memberNaming, true, true);
                            if(groupCacheEntry != null) {
                                member = new Member(groupCacheEntry.getName(), Member.MemberType.GROUP);
                            }
                        }
                    } else {
                        member = new Member(groupCacheEntry.getName(), Member.MemberType.GROUP);
                    }
                } else {
                    member = new Member(userCacheEntry.getName(), Member.MemberType.USER);
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

    /**
     * Retrieve the cache entry for a given username, if not found create a new one, and cache it if the param "cache" set to true
     * @param userName
     * @param cache
     * @return
     */
    private LDAPUserCacheEntry getUserCacheEntry(String userName, boolean cache){
        LDAPUserCacheEntry userCacheEntry = ldapCacheManager.getUserCacheEntryByName(getKey(), userName);
        if(userCacheEntry != null){
            if(userCacheEntry.getExist() != null && userCacheEntry.getExist() && userCacheEntry.getUser() != null){
                return userCacheEntry;
            } else if(userCacheEntry.getExist() != null && !userCacheEntry.getExist()){
                return userCacheEntry;
            }
        }

        List<String> userAttrs = getUserAttributes();
        UserNameClassPairCallbackHandler nameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(userCacheEntry);
        ldapTemplate.search(query().base(userConfig.getUidSearchName())
                .attributes(userAttrs.toArray(new String[userAttrs.size()]))
                .where(OBJECTCLASS_ATTRIBUTE).is(userConfig.getSearchObjectclass())
                .and(userConfig.getUidSearchAttribute()).is(userName),
                nameClassPairCallbackHandler);

        if(nameClassPairCallbackHandler.getCacheEntry() != null){
            userCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
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

    /**
     * Retrieve the cache entry for a given groupname, if not found create a new one, and cache it if the param "cache" set to true
     * @param groupName
     * @param cache
     * @return
     */
    private LDAPGroupCacheEntry getGroupCacheEntry(String groupName, boolean cache){
        LDAPGroupCacheEntry groupCacheEntry = ldapCacheManager.getGroupCacheEntryName(getKey(), groupName);
        if(groupCacheEntry != null){
            if(groupCacheEntry.getExist() != null && groupCacheEntry.getExist() && groupCacheEntry.getGroup() != null){
                return groupCacheEntry;
            } else if(groupCacheEntry.getExist() != null && !groupCacheEntry.getExist()){
                return groupCacheEntry;
            }
        }

        groupCacheEntry = getGroupCacheEntryByName(groupName, false, false);
        if(groupCacheEntry != null){
            return groupCacheEntry;
        } else {
            if (groupConfig.isDynamicEnabled()) {
                groupCacheEntry = getGroupCacheEntryByName(groupName, false, true);
            } else {
                groupCacheEntry = new LDAPGroupCacheEntry(groupName);
                groupCacheEntry.setExist(false);
            }
        }

        if(cache){
            ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
        }

        return groupCacheEntry;
    }

    /**
     * Retrieve the cache entry for a given groupname, if not found create a new one, and cache it if the param "cache" set to true
     * @param name
     * @param cache
     * @param isDynamic
     * @return
     */
    private LDAPGroupCacheEntry getGroupCacheEntryByName(String name, boolean cache, boolean isDynamic) {
        List<String> groupAttrs = getGroupAttributes(isDynamic);
        GroupNameClassPairCallbackHandler nameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(null, isDynamic);
        ldapTemplate.search(query().base(groupConfig.getSearchName())
                .attributes(groupAttrs.toArray(new String[groupAttrs.size()]))
                .where(OBJECTCLASS_ATTRIBUTE).is(isDynamic ? groupConfig.getDynamicSearchObjectclass() : groupConfig.getSearchObjectclass())
                .and(groupConfig.getSearchAttribute()).is(name),
                nameClassPairCallbackHandler);
        if(nameClassPairCallbackHandler.getCacheEntry() != null) {
            LDAPGroupCacheEntry ldapGroupCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
            if(cache) {
                ldapCacheManager.cacheGroup(getKey(), ldapGroupCacheEntry);
            }
            return ldapGroupCacheEntry;
        }
        return null;
    }

    /**
     * Retrieve the cache entry for a given dn, if not found create a new one, and cache it if the param "cache" set to true
     * @param dn
     * @param cache
     * @param isDynamic
     * @return
     */
    private LDAPGroupCacheEntry getGroupCacheEntryByDN(String dn, boolean cache, boolean isDynamic) {
        List<String> groupAttrs = getGroupAttributes(isDynamic);
        GroupNameClassPairCallbackHandler nameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(null, isDynamic);
        ldapTemplate.search(query().base(dn)
                .attributes(groupAttrs.toArray(new String[groupAttrs.size()]))
                .searchScope(SearchScope.OBJECT)
                .where(OBJECTCLASS_ATTRIBUTE).is(isDynamic ? groupConfig.getDynamicSearchObjectclass() : groupConfig.getSearchObjectclass()),
                nameClassPairCallbackHandler);
        if(nameClassPairCallbackHandler.getCacheEntry() != null) {
            LDAPGroupCacheEntry ldapGroupCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
            if(cache) {
                ldapCacheManager.cacheGroup(getKey(), ldapGroupCacheEntry);
            }
            return ldapGroupCacheEntry;
        }
        return null;
    }

    /**
     * Retrieve the cache entry for a given dn, if not found create a new one, and cache it if the param "cache" set to true
     * @param dn
     * @param cache
     * @return
     */
    private LDAPUserCacheEntry getUserCacheEntryByDN(String dn, boolean cache) {
        List<String> userAttrs = getUserAttributes();
        UserNameClassPairCallbackHandler nameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(null);
        ldapTemplate.search(query().base(dn)
                .attributes(userAttrs.toArray(new String[userAttrs.size()]))
                .searchScope(SearchScope.OBJECT)
                .where(OBJECTCLASS_ATTRIBUTE).is(userConfig.getSearchObjectclass()),
                nameClassPairCallbackHandler);

        if(nameClassPairCallbackHandler.getCacheEntry() != null) {
            LDAPUserCacheEntry ldapUserCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
            if(cache) {
                ldapCacheManager.cacheUser(getKey(), ldapUserCacheEntry);
            }
            return ldapUserCacheEntry;
        }
        return null;
    }

    /**
     * Retrieve the search attribute from a dn. If the dn does'nt contains the search attribute null is returned
     * @param dn
     * @param isUser
     * @return
     */
    private String getNameFromDn(String dn, boolean isUser) {
        LdapName ln = LdapUtils.newLdapName(dn);
        for (Rdn rdn : ln.getRdns()) {
            if (rdn.getType().equalsIgnoreCase(isUser ? userConfig.getUidSearchAttribute() : groupConfig.getSearchAttribute())) {
                return rdn.getValue().toString();
            }
        }

        return null;
    }

    /**
     * Callback handler for a single user, create the corresponding cache entry
     */
    private class UserNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        private LDAPUserCacheEntry cacheEntry;

        public LDAPUserCacheEntry getCacheEntry() {
            return cacheEntry;
        }

        private UserNameClassPairCallbackHandler(LDAPUserCacheEntry cacheEntry) {
            this.cacheEntry = cacheEntry;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            SearchResult searchResult = (SearchResult) nameClassPair;
            cacheEntry = attributesToUserCacheEntry(searchResult.getAttributes(), cacheEntry);
            cacheEntry.setDn(searchResult.getNameInNamespace());
        }
    }

    /**
     * Callback handler for a single group, create the corresponding cache entry
     */
    private class GroupNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        private LDAPGroupCacheEntry cacheEntry;
        private boolean isDynamic;

        public LDAPGroupCacheEntry getCacheEntry() {
            return cacheEntry;
        }

        private GroupNameClassPairCallbackHandler(LDAPGroupCacheEntry cacheEntry, boolean isDynamic) {
            this.cacheEntry = cacheEntry;
            this.isDynamic = isDynamic;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            SearchResult searchResult = (SearchResult) nameClassPair;
            cacheEntry = attributesToGroupCacheEntry(searchResult.getAttributes(), cacheEntry);
            cacheEntry.setDynamic(isDynamic);
            if(isDynamic && searchResult.getAttributes().get(groupConfig.getDynamicMembersAttribute()) != null) {
                cacheEntry.setDynamicMembersURL(searchResult.getAttributes().get(groupConfig.getDynamicMembersAttribute()).get().toString());
            }
            cacheEntry.setDn(searchResult.getNameInNamespace());
        }
    }

    /**
     * Callback handler for users, retrieve the list of usernames
     */
    private class UsersNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        private List<String> names = new ArrayList<String>();

        public List<String> getNames() {
            return names;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            SearchResult searchResult = (SearchResult) nameClassPair;
            LDAPUserCacheEntry cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
            UserNameClassPairCallbackHandler nameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(cacheEntry);
            nameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
            LDAPUserCacheEntry ldapUserCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
            ldapCacheManager.cacheUser(getKey(), ldapUserCacheEntry);
            names.add(ldapUserCacheEntry.getName());
        }
    }

    /**
     * Callback handler for groups, retrieve the list of groupnames
     */
    private class GroupsNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        private List<String> names = new ArrayList<String>();
        private boolean isDynamic;

        public List<String> getNames() {
            return names;
        }

        private GroupsNameClassPairCallbackHandler(boolean isDynamic) {
            this.isDynamic = isDynamic;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            SearchResult searchResult = (SearchResult) nameClassPair;
            LDAPGroupCacheEntry cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
            GroupNameClassPairCallbackHandler nameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(cacheEntry, isDynamic);
            nameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
            LDAPGroupCacheEntry ldapGroupCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
            ldapCacheManager.cacheGroup(getKey(), ldapGroupCacheEntry);
            names.add(ldapGroupCacheEntry.getName());
        }
    }

    /**
     * Calback handler for dynamic members, retrieve the list of members
     */
    private class DynMembersNameClassPairCallbackHandler implements NameClassPairCallbackHandler {
        List<Member> members = Lists.newArrayList();

        public List<Member> getMembers() {
            return members;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            SearchResult searchResult = (SearchResult) nameClassPair;

            // try to know if we deal with a group or a user
            Boolean isUser = guessUserOrGroupFromDN(searchResult.getNameInNamespace());

            // try to retrieve the object from the cache
            LDAPAbstractCacheEntry cacheEntry;
            if (isUser != null){
                if(isUser) {
                    cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                } else {
                    cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                }
            } else {
                // look in all cache
                cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                if(cacheEntry == null) {
                    cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                    isUser = cacheEntry != null ? false : null;
                } else {
                    isUser = true;
                }
            }
            if(cacheEntry != null) {
                if(isUser) {
                    logger.debug("Dynamic member: " + searchResult.getNameInNamespace() + " retrieved from cache and resolved as a user");
                    members.add(new Member(cacheEntry.getName(), Member.MemberType.USER));
                } else {
                    logger.debug("Dynamic member: " + searchResult.getNameInNamespace() + " retrieved from cache and resolved as a group");
                    members.add(new Member(cacheEntry.getName(), Member.MemberType.GROUP));
                }
            }

            // try the objectclass
            Boolean isDynamic = false;
            searchResult.getAttributes().get(OBJECTCLASS_ATTRIBUTE).getAll();
            List<String> objectclasses = new ArrayList<String>();
            LdapUtils.collectAttributeValues(searchResult.getAttributes(), OBJECTCLASS_ATTRIBUTE, objectclasses, String.class);
            if(objectclasses.contains(userConfig.getSearchObjectclass())){
                isUser = true;
            } else if (objectclasses.contains(groupConfig.getSearchObjectclass())) {
                isUser = false;
            } else if(groupConfig.isDynamicEnabled() && objectclasses.contains(groupConfig.getDynamicSearchObjectclass())){
                isUser = false;
                isDynamic = true;
            }
            if(isUser != null) {
                if (isUser) {
                    handleUserNameClassPair(nameClassPair, searchResult);
                } else {
                    handleGroupNameClassPair(nameClassPair, searchResult, isDynamic);
                }
                return;
            }

            // try to guess the type on attributes present in the searchresult
            List<String> searchResultsAttr = new ArrayList<String>();
            NamingEnumeration<String> attrs = searchResult.getAttributes().getIDs();
            while (attrs.hasMore()){
                searchResultsAttr.add(attrs.next());
            }
            List<String> commonUserAttrs = getCommonAttributesSize(searchResultsAttr, getUserAttributes());
            List<String> commonGroupAttrs = getCommonAttributesSize(searchResultsAttr, getGroupAttributes(isDynamic));
            if(commonUserAttrs.size() > 0 && commonUserAttrs.contains(userConfig.getUidSearchAttribute()) && commonUserAttrs.size() > commonGroupAttrs.size()){
                handleUserNameClassPair(nameClassPair, searchResult);
                return;
            } else if(commonGroupAttrs.size() > 0 && commonGroupAttrs.contains(groupConfig.getSearchAttribute())) {
                handleGroupNameClassPair(nameClassPair, searchResult, false);
                return;
            }

            // type not resolved
            logger.warn("Dynamic member: " + searchResult.getNameInNamespace() + " not resolved as a user or a group");
        }

        private void handleGroupNameClassPair(NameClassPair nameClassPair, SearchResult searchResult, Boolean isDynamic) throws NamingException {
            GroupNameClassPairCallbackHandler groupNameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(null, isDynamic);
            groupNameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
            LDAPGroupCacheEntry groupCacheEntry = groupNameClassPairCallbackHandler.getCacheEntry();
            ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
            members.add(new Member(groupCacheEntry.getName(), Member.MemberType.GROUP));
            logger.debug("Dynamic member: " + searchResult.getNameInNamespace() + " resolved as a " + (isDynamic ? " dynamic group" : " group"));
        }

        private void handleUserNameClassPair(NameClassPair nameClassPair, SearchResult searchResult) throws NamingException {
            UserNameClassPairCallbackHandler userNameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(null);
            userNameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
            LDAPUserCacheEntry userCacheEntry = userNameClassPairCallbackHandler.getCacheEntry();
            ldapCacheManager.cacheUser(getKey(), userCacheEntry);
            members.add(new Member(userCacheEntry.getName(), Member.MemberType.USER));
            logger.debug("Dynamic member: " + searchResult.getNameInNamespace() + " resolved as a user");
        }
    }

    /**
     * Populate the given cache entry or create new one if the given is null with the LDAP attributes
     * @param attrs
     * @param userCacheEntry
     * @return
     * @throws NamingException
     */
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

    /**
     * Populate the given cache entry or create new one if the given is null with the LDAP attributes
     * @param attrs
     * @param groupCacheEntry
     * @return
     * @throws NamingException
     */
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

    /**
     * Map ldap attributes to jahia properties
     * @param attributes
     * @param isUser
     * @return
     */
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

    /**
     * build a user query, that use the searchCriteria from jahia forms
     * @param searchCriteria
     * @return
     */
    private ContainerCriteria buildUserQuery(Properties searchCriteria) {
        List<String> attributesToRetrieve = getUserAttributes();

        ContainerCriteria query = query().base(userConfig.getUidSearchName())
                .attributes(attributesToRetrieve.toArray(new String[attributesToRetrieve.size()]))
                .countLimit((int) userConfig.getSearchCountlimit())
                .where(OBJECTCLASS_ATTRIBUTE).is(StringUtils.defaultString(userConfig.getSearchObjectclass(), "*"));

        // transform jnt:user props to ldap props
        Properties ldapfilters = mapJahiaPropertiesToLDAP(searchCriteria, userConfig.getAttributesMapper());

        // define and / or operator
        boolean orOp = isOrOperator(ldapfilters, searchCriteria);

        // process the user specific filters
        ContainerCriteria filterQuery = getQueryFilters(ldapfilters, userConfig, orOp);

        if(filterQuery != null){
            query.and(filterQuery);
        }

        return query;
    }

    /**
     * Build a group query based on search criteria
     * @param searchCriteria
     * @param isDynamic
     * @return
     */
    private ContainerCriteria buildGroupQuery(Properties searchCriteria, boolean isDynamic) {
        List<String> attributesToRetrieve = getGroupAttributes(isDynamic);
        if(isDynamic) {
            attributesToRetrieve.add(groupConfig.getDynamicMembersAttribute());
        }

        ContainerCriteria query = query().base(groupConfig.getSearchName())
                .attributes(attributesToRetrieve.toArray(new String[attributesToRetrieve.size()]))
                .countLimit((int) groupConfig.getSearchCountlimit())
                .where(OBJECTCLASS_ATTRIBUTE).is(isDynamic ? groupConfig.getDynamicSearchObjectclass() : groupConfig.getSearchObjectclass());

        // transform jnt:user props to ldap props
        Properties ldapfilters = mapJahiaPropertiesToLDAP(searchCriteria, groupConfig.getAttributesMapper());

        // define and / or operator
        boolean orOp = isOrOperator(ldapfilters, searchCriteria);

        // process the user specific filters
        ContainerCriteria filterQuery = getQueryFilters(ldapfilters, groupConfig, orOp);

        if(filterQuery != null){
            query.and(filterQuery);
        }

        return query;
    }

    private boolean isOrOperator(Properties ldapfilters, Properties searchCriteria) {
        if (ldapfilters.size() > 1) {
            if (searchCriteria.containsKey(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)) {
                if (((String) searchCriteria.get(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)).trim().toLowerCase().equals("and")) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Construct the filters for queries
     * @param ldapfilters
     * @param config
     * @param isOrOperator
     * @return
     */
    private ContainerCriteria getQueryFilters(Properties ldapfilters, AbstractConfig config,  boolean isOrOperator){
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
                    addCriteriaToQuery(filterQuery, isOrOperator, filterName).like(filterValue);
                }
            }
        }
        return filterQuery;
    }

    private ConditionCriteria addCriteriaToQuery(ContainerCriteria query, boolean isOr, String attribute){
        if (isOr){
            return query.or(attribute);
        } else {
            return query.and(attribute);
        }
    }

    /**
     * Map jahia properties to ldap properties
     * @param searchCriteria
     * @param configProperties
     * @return
     */
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

    /**
     * Try to guess if the given dn is a user or a group
     * @param dn
     * @return
     * @throws InvalidNameException
     */
    private Boolean guessUserOrGroupFromDN(String dn) throws InvalidNameException {
        Boolean isUser = null;
        final LdapName memberLdapName = LdapUtils.newLdapName(dn);
        if (memberLdapName.startsWith(new LdapName(userConfig.getUidSearchName()))) {
            // it's a user
            isUser = distinctBase ? true : null;
        } else if (memberLdapName.startsWith(new LdapName(groupConfig.getSearchName()))) {
            // it's a group
            isUser = distinctBase ? false : null;
        }
        return isUser;
    }

    /**
     * get user ldap attributes that need to be return from the ldap
     * @return
     */
    private List<String> getUserAttributes() {
        List<String> attrs = new ArrayList<String>(userConfig.getAttributesMapper().values());
        attrs.add(userConfig.getUidSearchAttribute());
        return attrs;
    }

    /**
     * get group ldap attributes that need to be return from the ldap
     * @return
     */
    private List<String> getGroupAttributes(boolean isDynamic) {
        List<String> attrs = new ArrayList<String>(groupConfig.getAttributesMapper().values());
        attrs.add(groupConfig.getSearchAttribute());
        if (isDynamic) {
            attrs.add(groupConfig.getDynamicMembersAttribute());
        }
        return attrs;
    }

    /**
     * Construct a list that contain only the elements also contains from the other list
     * @param first
     * @param second
     * @return
     */
    private List<String> getCommonAttributesSize(List<String> first, List<String> second) {
        List<String> commons = new ArrayList<String>(first);
        commons.retainAll(second);
        return commons;
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

    @Override
    public boolean supportsGroups() {
        return true;
    }
}

