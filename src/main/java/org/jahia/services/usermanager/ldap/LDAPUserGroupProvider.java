/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.services.usermanager.ldap;

import com.google.common.collect.Lists;
import com.sun.jndi.ldap.LdapURL;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.reflect.FieldUtils;
import org.apache.jackrabbit.util.Text;
import org.jahia.modules.external.users.*;
import org.jahia.services.content.decorator.JCRMountPointNode;
import org.jahia.services.usermanager.*;
import org.jahia.services.usermanager.ldap.cache.LDAPAbstractCacheEntry;
import org.jahia.services.usermanager.ldap.cache.LDAPCacheManager;
import org.jahia.services.usermanager.ldap.cache.LDAPGroupCacheEntry;
import org.jahia.services.usermanager.ldap.cache.LDAPUserCacheEntry;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateCallback;
import org.jahia.services.usermanager.ldap.communication.LdapTemplateWrapper;
import org.jahia.services.usermanager.ldap.config.AbstractConfig;
import org.jahia.services.usermanager.ldap.config.GroupConfig;
import org.jahia.services.usermanager.ldap.config.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.InsufficientResourcesException;
import org.springframework.ldap.ServiceUnavailableException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.NameClassPairCallbackHandler;
import org.springframework.ldap.core.support.DefaultIncrementalAttributesMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.query.ConditionCriteria;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.ldap.query.SearchScope;
import org.springframework.ldap.support.LdapUtils;

import javax.jcr.RepositoryException;
import javax.naming.InvalidNameException;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.HardcodedFilter;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * Implementation of UserGroupProvider for Spring LDAP
 *
 * @author david
 */
public class LDAPUserGroupProvider extends BaseUserGroupProvider {

    public static final int CONNECTION_ERROR_CACHE_TTL = 5;
    protected static final String OBJECTCLASS_ATTRIBUTE = "objectclass";
    private static Logger logger = LoggerFactory.getLogger(LDAPUserGroupProvider.class);

    private static final String PROP_USERNAME = "username";
    private static final String PROP_GROUPNAME = "groupname";

    private LdapContextSource contextSource;
    private LdapTemplateWrapper ldapTemplateWrapper;

    // Configs
    private UserConfig userConfig;
    private GroupConfig groupConfig;
    private boolean distinctBase = false; // if user and group are different

    // Cache
    private LDAPCacheManager ldapCacheManager;
    private volatile ContainerCriteria searchGroupCriteria;
    private volatile ContainerCriteria searchGroupDynamicCriteria;

    private AtomicInteger timeoutCount = new AtomicInteger(0);
    private int maxLdapTimeoutCountBeforeDisconnect = 3;

    private ContainerCriteria groupSearchFilterCriteria;
    private ContainerCriteria userSearchFilterCriteria;

    /**
     * If a pre-defined group search filter was configured, apply it on the provided query.
     *
     * @param query the search query to apply the group filter on
     * @return the adjusted query
     */
    private ContainerCriteria applyPredefinedGroupFilter(ContainerCriteria query) {
        ContainerCriteria userFilter = getGroupSearchFilterCriteria();
        if (userFilter != null) {
            query.and(userFilter);
        }

        return query;
    }

    /**
     * If a pre-defined user search filter was configured, apply it on the provided query.
     *
     * @param query the search query to apply the user filter on
     * @return the adjusted query
     */
    private ContainerCriteria applyPredefinedUserFilter(ContainerCriteria query) {
        ContainerCriteria userFilter = getUserSearchFilterCriteria();
        if (userFilter != null) {
            query.and(userFilter);
        }

        return query;
    }

    @Override
    public JahiaUser getUser(String name) throws UserNotFoundException {
        LDAPUserCacheEntry userCacheEntry = getUserCacheEntry(name, true);
        if (!userCacheEntry.getExist()) {
            throw new UserNotFoundException("unable to find user " + name + " on provider " + getKey());
        } else {
            return userCacheEntry.getUser();
        }
    }

    @Override
    public JahiaGroup getGroup(String name) throws GroupNotFoundException {
        LDAPGroupCacheEntry groupCacheEntry = getGroupCacheEntry(name, true);
        if (!groupCacheEntry.getExist()) {
            throw new GroupNotFoundException("unable to find group " + name + " on provider " + getKey());
        } else {
            return groupCacheEntry.getGroup();
        }
    }

    @Override
    public List<Member> getGroupMembers(String groupName) {

        LDAPGroupCacheEntry groupCacheEntry = getGroupCacheEntry(groupName, false);
        if (!groupCacheEntry.getExist()) {
            return Collections.emptyList();
        }
        if (groupCacheEntry.getMembers() != null) {
            return new ArrayList<>(groupCacheEntry.getMembers());
        }

        List<Member> members = null;
        if (groupCacheEntry.isDynamic() && StringUtils.isNotEmpty(groupCacheEntry.getDynamicMembersURL())) {
            members = loadMembersFromUrl(groupCacheEntry.getDynamicMembersURL());
        } else {
            members = loadMembersFromDN(groupCacheEntry.getDn());
        }

        if (CollectionUtils.isNotEmpty(members)) {
            groupCacheEntry.setMembers(members);
            ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
            return new ArrayList<>(groupCacheEntry.getMembers());
        } else {
            return Collections.emptyList();
        }
    }

    private boolean isDynamicGroupMembers(String userId, String groupName) {

        final LDAPGroupCacheEntry groupCacheEntry = getGroupCacheEntry(groupName, false);
        if (!groupCacheEntry.getExist()) {
            return false;
        }

        final List<String> membersId = new ArrayList<>();
        final List<Member> members = new ArrayList<>();
        if (groupCacheEntry.getMembers() == null) {
            if (groupCacheEntry.isDynamic() && StringUtils.isNotEmpty(groupCacheEntry.getDynamicMembersURL())) {
                try {
                    final String dynamicMembersURL = groupCacheEntry.getDynamicMembersURL();
                    final LdapURL ldapURL = new LdapURL(dynamicMembersURL);
                    final String ldapFilter = ldapURL.getFilter();
                    final Filter dynamicFilter = (new AndFilter())
                            .and(new HardcodedFilter(ldapFilter))
                            .and(new HardcodedFilter(String.format("(%s=%s)", userConfig.getUidSearchAttribute(), userId)));
                    final String url = dynamicMembersURL.replace(ldapFilter, dynamicFilter.toString());

                    members.addAll(loadMembersFromUrl(url));
                } catch (NamingException ex) {
                    logger.error("Error trying to get dynamic members from url: {}", groupCacheEntry.getDynamicMembersURL());
                }
            }
        } else {
            members.addAll(groupCacheEntry.getMembers());
        }

        for (Member member : members) {
            membersId.add(member.getName());
        }

        return membersId.contains(userId);
    }

    @Override
    public List<String> getMembership(final Member member) {

        boolean isGroup = member.getType().equals(Member.MemberType.GROUP);

        if (isGroup && !userConfig.isCanGroupContainSubGroups()) {
            return Collections.emptyList();
        }
        LDAPAbstractCacheEntry cacheEntry = isGroup ? getGroupCacheEntry(member.getName(), false) : getUserCacheEntry(member.getName(), false);
        if (cacheEntry.getMemberships() != null) {
            return new ArrayList<>(cacheEntry.getMemberships());
        }
        if (!cacheEntry.getExist()) {
            return null;
        }

        final String dn = cacheEntry.getDn();
        long startTime = System.currentTimeMillis();
        List<String> memberships = ldapTemplateWrapper.execute(new BaseLdapActionCallback<List<String>>(getExternalUserGroupService(), getKey()) {

            @Override
            public List<String> doInLdap(LdapTemplate ldapTemplate) {
                return ldapTemplate.search(
                        applyPredefinedGroupFilter(query().base(groupConfig.getSearchName())
                                .attributes(groupConfig.getSearchAttribute())
                                .where(OBJECTCLASS_ATTRIBUTE)
                                .is(groupConfig.getSearchObjectclass())
                                .and(groupConfig.getMembersAttribute())
                                .like(dn)),
                        new AttributesMapper<String>() {

                    @Override
                    public String mapFromAttributes(Attributes attrs) throws NamingException {
                        return encode(attrs.get(groupConfig.getSearchAttribute()).get().toString());
                    }
                });
            }
        });
        if (logger.isDebugEnabled()) {
            logger.debug("Query getMembership for {} / {} dn={} in {} ms", new Object[] {
                member.getName(),
                groupConfig.getSearchAttribute(),
                dn,
                System.currentTimeMillis() - startTime
            });
        }

        // in case of communication error, the result may be null
        if (memberships == null) {
            memberships = new ArrayList<>();
        }

        if (groupConfig.isDynamicEnabled()) {
            Properties searchCriteria = new Properties();
            searchCriteria.put("*", "*");
            List<String> dynGroups = searchGroups(searchCriteria, true);
            for (String dynGroup : dynGroups) {
                if(isDynamicGroupMembers(member.getName(), dynGroup)) {
                    memberships.add(dynGroup);
                }
            }
        }

        cacheEntry.setMemberships(memberships);
        if (isGroup) {
            ldapCacheManager.cacheGroup(getKey(), (LDAPGroupCacheEntry) cacheEntry);
        } else {
            ldapCacheManager.cacheUser(getKey(), (LDAPUserCacheEntry) cacheEntry);
        }

        return new ArrayList<>(cacheEntry.getMemberships());
    }

    @Override
    public List<String> searchUsers(final Properties searchCriteria, long offset, long limit) {
        if (searchCriteria.containsKey(PROP_USERNAME) && searchCriteria.size() == 1 && !searchCriteria.getProperty(PROP_USERNAME).contains("*")) {
            try {
                JahiaUser user = getUser((String) searchCriteria.get(PROP_USERNAME));
                return Collections.singletonList(user.getUsername());
            } catch (UserNotFoundException e) {
                return Collections.emptyList();
            }
        }
        final ContainerCriteria query = buildUserQuery(searchCriteria);
        if (query == null) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        final List<String> names = ldapTemplateWrapper.execute(new BaseLdapActionCallback<List<String>>(getExternalUserGroupService(), getKey()) {

            @Override
            public List<String> doInLdap(LdapTemplate ldapTemplate) {
                final UsersNameClassPairCallbackHandler searchNameClassPairCallbackHandler = new UsersNameClassPairCallbackHandler();
                ldapTemplate.search(query, searchNameClassPairCallbackHandler);
                return searchNameClassPairCallbackHandler.getNames();
            }
        });

        if (logger.isDebugEnabled()) {
            logger.debug("Search users for criteria {} using filter {} done in {} ms. Found {} entries.", new Object[]{
                searchCriteria, query.filter(), System.currentTimeMillis() - startTime, names.size()});
        }

        return names.subList(Math.min((int) offset, names.size()), limit < 0 ? names.size() : Math.min((int) (offset + limit), names.size()));
    }

    @Override
    public List<String> searchGroups(Properties searchCriteria, long offset, long limit) {
        if (searchCriteria.containsKey(PROP_GROUPNAME) && searchCriteria.size() == 1 && !searchCriteria.getProperty(PROP_GROUPNAME).contains("*")) {
            try {
                JahiaGroup group = getGroup((String) searchCriteria.get(PROP_GROUPNAME));
                return Arrays.asList(group.getGroupname());
            } catch (GroupNotFoundException e) {
                return Collections.emptyList();
            }
        }

        List<String> groups = searchGroups(searchCriteria, false);

        // handle dynamics
        if (groupConfig.isDynamicEnabled()) {
            groups.addAll(searchGroups(searchCriteria, true));
        }

        return groups.subList(Math.min((int) offset, groups.size()), limit < 0 ? groups.size() : Math.min((int) (offset + limit), groups.size()));
    }

    @Override
    public boolean verifyPassword(String userName, String userPassword) {
        logger.debug("Verifying password for {}...", userName);
        DirContext ctx = null;
        try {
            LDAPUserCacheEntry userCacheEntry = getUserCacheEntry(userName, true);
            if (userCacheEntry.getExist()) {
                long startTime = System.currentTimeMillis();
                ctx = contextSource.getContext(userCacheEntry.getDn(), userPassword);
                // Take care here - if a base was specified on the ContextSource
                // that needs to be removed from the user DN for the lookup to succeed.
                ctx.lookup(LdapUtils.newLdapName(userCacheEntry.getDn()));
                logger.debug("Password verified for {} in {} ms", userName, System.currentTimeMillis() - startTime);
                return true;
            }
        } catch (NamingException | org.springframework.ldap.NamingException e) {
            // Context creation failed - authentication did not succeed
            logger.warn("Login failed for user {}: {} (enable debug for full stacktrace)", userName, e.getMessage());
            logger.debug(e.getMessage(), e);
        } finally {
            LdapUtils.closeContext(ctx);
        }
        return false;
    }

    @Override
    public boolean isAvailable() throws RepositoryException {

        // do a simple search on users to check the availability
        long startTime = System.currentTimeMillis();
        final Exception[] exception = new Exception[1];
        boolean available = ldapTemplateWrapper.execute(new BaseLdapActionCallback<Boolean>(getExternalUserGroupService(), getKey()) {

            @Override
            public Boolean doInLdap(LdapTemplate ldapTemplate) {

                ldapTemplate.search(buildUserQuery(new Properties()), new NameClassPairCallbackHandler() {

                    @Override
                    public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {

                    }
                });
                return true;
            }

            @Override
            public Boolean onError(Exception e) {
                super.onError(e);
                exception[0] = e;
                return timeoutCount.get() < maxLdapTimeoutCountBeforeDisconnect;
            }
        });
        logger.debug("Is available in {} ms", System.currentTimeMillis() - startTime);

        if (!available) {
            // throw an exception instead of return false to display a custom message with the ldap server url.
            throw new RepositoryException("LDAP Server '" + userConfig.getUrl() + "' is not reachable", exception[0]);
        } else {
            return true;
        }
    }

    private List<String> searchGroups(final Properties searchCriteria, boolean isDynamics) {

        final ContainerCriteria query = getGroupQuery(searchCriteria, isDynamics);
        if (query == null) {
            return Collections.emptyList();
        }
        final GroupsNameClassPairCallbackHandler searchNameClassPairCallbackHandler = new GroupsNameClassPairCallbackHandler(isDynamics);
        long startTime = System.currentTimeMillis();
        final List<String> names = ldapTemplateWrapper.execute(new BaseLdapActionCallback<List<String>>(getExternalUserGroupService(), getKey()) {

            @Override
            public List<String> doInLdap(LdapTemplate ldapTemplate) {
                ldapTemplate.search(query, searchNameClassPairCallbackHandler);
                return searchNameClassPairCallbackHandler.getNames();
            }
        });

        if (logger.isDebugEnabled()) {
            logger.debug("Search groups for criteria {} using filter {} done in {} ms. Found {} entries.", new Object[]{
                searchCriteria, query.filter(), System.currentTimeMillis() - startTime, names.size()});
        }

        return names;
    }

    /**
     * get the members from a ldap URL used for dynamic groups
     *
     * @param url
     * @return
     */
    private List<Member> loadMembersFromUrl(String url) {

        try {

            final LdapURL ldapURL = new LdapURL(url);
            final Set<String> attrs = new HashSet<>(getUserAttributes());
            attrs.addAll(getGroupAttributes(true));
            if (groupConfig.isDynamicEnabled()) {
                attrs.add(groupConfig.getDynamicSearchObjectclass());
            }
            attrs.add(OBJECTCLASS_ATTRIBUTE);

            final SearchScope searchScope;
            if ("one".equalsIgnoreCase(ldapURL.getScope())) {
                searchScope = SearchScope.ONELEVEL;
            } else if ("base".equalsIgnoreCase(ldapURL.getScope())) {
                searchScope = SearchScope.OBJECT;
            } else {
                searchScope = SearchScope.SUBTREE;
            }

            long startTime = System.currentTimeMillis();
            final List<Member> members = ldapTemplateWrapper.execute(new BaseLdapActionCallback<List<Member>>(getExternalUserGroupService(), getKey()) {

                @Override
                public List<Member> doInLdap(LdapTemplate ldapTemplate) {
                    final DynMembersNameClassPairCallbackHandler nameClassPairCallbackHandler = new DynMembersNameClassPairCallbackHandler();
                    ldapTemplate.search(query()
                            .base(ldapURL.getDN())
                            .attributes(attrs.toArray(new String[attrs.size()]))
                            .searchScope(searchScope)
                            .filter(ldapURL.getFilter()),
                            nameClassPairCallbackHandler);
                    return nameClassPairCallbackHandler.getMembers();
                }
            });
            logger.debug("Load members from url {} in {} ms", url, System.currentTimeMillis() - startTime);

            return members;
        } catch (NamingException e) {
            logger.error("Error trying to get dynamic members from url: {}", url);
        }
        return null;
    }

    /**
     * get the members from a group DN
     *
     * @param groupDN
     * @return
     */
    private List<Member> loadMembersFromDN(final String groupDN) {

        long startTime = System.currentTimeMillis();
        final LdapName groupName = LdapUtils.newLdapName(groupDN);

        NamingEnumeration<?> members = ldapTemplateWrapper.execute(new BaseLdapActionCallback<NamingEnumeration<?>>(getExternalUserGroupService(), getKey()) {

            @Override
            public NamingEnumeration<?> doInLdap(LdapTemplate ldapTemplate) {

                // use AD range search if a range is specify in the conf
                if (groupConfig.getAdRangeStep() > 0) {

                    DefaultIncrementalAttributesMapper incrementalAttributesMapper = new DefaultIncrementalAttributesMapper(groupConfig.getAdRangeStep(), groupConfig.getMembersAttribute());
                    while (incrementalAttributesMapper.hasMore()) {
                        ldapTemplate.lookup(groupName, incrementalAttributesMapper.getAttributesForLookup(), incrementalAttributesMapper);
                    }
                    Attributes attributes = incrementalAttributesMapper.getCollectedAttributes();
                    try {
                        return attributes.get(groupConfig.getMembersAttribute()).getAll();
                    } catch (NamingException e) {
                        logger.error("Error retrieving the LDAP members using range on group: " + groupDN, e);
                    }
                } else {
                    return ldapTemplate.lookup(groupName, new String[]{groupConfig.getMembersAttribute()}, new AttributesMapper<NamingEnumeration<?>>() {

                        @Override
                        public NamingEnumeration<?> mapFromAttributes(Attributes attributes) throws NamingException {
                            return attributes.get(groupConfig.getMembersAttribute()) != null ? attributes.get(groupConfig.getMembersAttribute()).getAll() : null;
                        }
                    });
                }
                return null;
            }
        });
        logger.debug("Load group members {} in {} ms", groupDN, System.currentTimeMillis() - startTime);

        return loadMembers(members);
    }

    private List<Member> loadMembers(NamingEnumeration<?> members) {

        List<Member> memberList = new ArrayList<>();
        try {

            while (members != null && members.hasMore()) {

                final String memberNaming = (String) members.next();
                // try to know if we deal with a group or a user
                Boolean isUser = null;
                if (userConfig.isCanGroupContainSubGroups()) {
                    isUser = guessUserOrGroupFromDN(memberNaming);
                } else {
                    isUser = true;
                }

                // try to retrieve the object from the cache
                LDAPAbstractCacheEntry cacheEntry;
                if (isUser != null) {
                    if (isUser) {
                        cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), memberNaming);
                    } else {
                        cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), memberNaming);
                    }
                } else {
                    // look in all cache
                    cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), memberNaming);
                    if (cacheEntry == null) {
                        cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), memberNaming);
                        isUser = cacheEntry != null ? false : null;
                    } else {
                        isUser = true;
                    }
                }
                if (cacheEntry != null) {
                    if (isUser) {
                        memberList.add(new Member(cacheEntry.getName(), Member.MemberType.USER));
                    } else {
                        memberList.add(new Member(cacheEntry.getName(), Member.MemberType.GROUP));
                    }
                    continue;
                }

                // try to retrieve
                if (isUser != null && userConfig.isSearchAttributeInDn()) {
                    String name = getNameFromDn(memberNaming, isUser);
                    if (StringUtils.isNotEmpty(name)) {
                        memberList.add(isUser ? new Member(name, Member.MemberType.USER) : new Member(name, Member.MemberType.GROUP));
                        continue;
                    }
                }

                // do queries
                // and cache the result
                Member member = null;
                LDAPUserCacheEntry userCacheEntry = getUserCacheEntryByDN(memberNaming, true);
                if (userCacheEntry == null) {
                    // look in groups
                    LDAPGroupCacheEntry groupCacheEntry = getGroupCacheEntryByDN(memberNaming, true, false);
                    if (groupCacheEntry == null) {
                        if (groupConfig.isDynamicEnabled()) {
                            // look in dynamic groups
                            groupCacheEntry = getGroupCacheEntryByDN(memberNaming, true, true);
                            if (groupCacheEntry != null) {
                                member = new Member(groupCacheEntry.getName(), Member.MemberType.GROUP);
                            }
                        }
                    } else {
                        member = new Member(groupCacheEntry.getName(), Member.MemberType.GROUP);
                    }
                } else {
                    member = new Member(userCacheEntry.getName(), Member.MemberType.USER);
                }

                if (member != null) {
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
     *
     * @param userName
     * @param cache
     * @return
     */
    private LDAPUserCacheEntry getUserCacheEntry(final String userName, boolean cache) {

        LDAPUserCacheEntry userCacheEntry = ldapCacheManager.getUserCacheEntryByName(getKey(), userName);
        if (userCacheEntry != null) {
            if (userCacheEntry.getExist() != null && userCacheEntry.getExist() && userCacheEntry.getUser() != null) {
                return userCacheEntry;
            } else if (userCacheEntry.getExist() != null && !userCacheEntry.getExist()) {
                return userCacheEntry;
            }
        }

        final List<String> userAttrs = getUserAttributes();
        final UserNameClassPairCallbackHandler nameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(userCacheEntry);
        long startTime = System.currentTimeMillis();

        boolean validLdapCall = ldapTemplateWrapper.execute(new BaseLdapActionCallback<Boolean>(getExternalUserGroupService(), getKey()) {
            @Override
            public Boolean doInLdap(LdapTemplate ldapTemplate) {
                ldapTemplate.search(applyPredefinedUserFilter(query().base(userConfig.getUidSearchName())
                        .attributes(userAttrs.toArray(new String[userAttrs.size()]))
                        .where(OBJECTCLASS_ATTRIBUTE).is(userConfig.getSearchObjectclass())
                        .and(userConfig.getUidSearchAttribute()).is(decode(userName))),
                        nameClassPairCallbackHandler);
                return true;
            }

            @Override
            public Boolean onError(Exception e) {
                super.onError(e);
                return false;
            }
        });
        if (logger.isDebugEnabled()) {
            logger.debug("Get user {} in {} ms", userName, System.currentTimeMillis() - startTime);
        }

        if (nameClassPairCallbackHandler.getCacheEntry() != null) {
            userCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
            userCacheEntry.setExist(true);
        } else {
            userCacheEntry = new LDAPUserCacheEntry(userName);
            userCacheEntry.setExist(false);
        }

        if (cache && validLdapCall) {
            ldapCacheManager.cacheUser(getKey(), userCacheEntry);
        }

        return userCacheEntry;
    }

    /**
     * Retrieve the cache entry for a given groupname, if not found create a new one, and cache it if the param "cache" set to true
     *
     * @param groupName
     * @param cache
     * @return
     */
    private LDAPGroupCacheEntry getGroupCacheEntry(String groupName, boolean cache) {

        LDAPGroupCacheEntry groupCacheEntry = ldapCacheManager.getGroupCacheEntryName(getKey(), groupName);
        if (groupCacheEntry != null) {
            if (groupCacheEntry.getExist() != null && groupCacheEntry.getExist() && groupCacheEntry.getGroup() != null) {
                return groupCacheEntry;
            } else if (groupCacheEntry.getExist() != null && !groupCacheEntry.getExist()) {
                return groupCacheEntry;
            }
        }

        try {
            groupCacheEntry = getGroupCacheEntryByName(groupName, false, false);
            if (groupCacheEntry == null) {
                if (groupConfig.isDynamicEnabled()) {
                    groupCacheEntry = getGroupCacheEntryByName(groupName, false, true);
                } else {
                    groupCacheEntry = new LDAPGroupCacheEntry(groupName);
                    groupCacheEntry.setExist(false);
                }
            }
        } catch (Exception e) {
            // Exception already logged, skip cache and return null
            return null;
        }

        if (cache) {
            ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
        }

        return groupCacheEntry;
    }

    /**
     * Retrieve the cache entry for a given groupname, if not found create a new one, and cache it if the param "cache" set to true
     *
     * @param name
     * @param cache
     * @param isDynamic
     * @return
     */
    private LDAPGroupCacheEntry getGroupCacheEntryByName(final String name, boolean cache, final boolean isDynamic) throws Exception {

        final List<String> groupAttrs = getGroupAttributes(isDynamic);
        final GroupNameClassPairCallbackHandler nameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(null, isDynamic);
        long startTime = System.currentTimeMillis();
        final Exception[] exceptions = new Exception[1];

        boolean validLdapCall = ldapTemplateWrapper.execute(new BaseLdapActionCallback<Boolean>(getExternalUserGroupService(), getKey()) {

            @Override
            public Boolean doInLdap(LdapTemplate ldapTemplate) {
                ldapTemplate.search(applyPredefinedGroupFilter(query().base(groupConfig.getSearchName())
                        .attributes(groupAttrs.toArray(new String[groupAttrs.size()]))
                        .where(OBJECTCLASS_ATTRIBUTE).is(isDynamic ? groupConfig.getDynamicSearchObjectclass() : groupConfig.getSearchObjectclass())
                        .and(groupConfig.getSearchAttribute()).is(decode(name))),
                        nameClassPairCallbackHandler);
                return true;
            }

            @Override
            public Boolean onError(Exception e) {
                exceptions[0] = e;
                super.onError(e);
                return false;
            }
        });

        if (!validLdapCall) {
            throw exceptions[0];
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Get group {} in {} ms", name, System.currentTimeMillis() - startTime);
        }

        return getAndCacheGroupEntry(nameClassPairCallbackHandler, cache);
    }

    /**
     * Retrieve the cache entry for a given dn, if not found create a new one, and cache it if the param "cache" set to true
     *
     * @param dn
     * @param cache
     * @param isDynamic
     * @return
     */
    private LDAPGroupCacheEntry getGroupCacheEntryByDN(final String dn, final boolean cache, final boolean isDynamic) {

        final List<String> groupAttrs = getGroupAttributes(isDynamic);
        final GroupNameClassPairCallbackHandler nameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(null, isDynamic);
        long startTime = System.currentTimeMillis();
        final LDAPGroupCacheEntry groupCacheEntry = ldapTemplateWrapper.execute(new BaseLdapActionCallback<LDAPGroupCacheEntry>(getExternalUserGroupService(), getKey()) {

            @Override
            public LDAPGroupCacheEntry doInLdap(LdapTemplate ldapTemplate) {
                ldapTemplate.search(applyPredefinedGroupFilter(query().base(dn)
                        .attributes(groupAttrs.toArray(new String[groupAttrs.size()]))
                        .searchScope(SearchScope.OBJECT)
                        .where(OBJECTCLASS_ATTRIBUTE).is(isDynamic ? groupConfig.getDynamicSearchObjectclass() : groupConfig.getSearchObjectclass())),
                        nameClassPairCallbackHandler);
                return getAndCacheGroupEntry(nameClassPairCallbackHandler, cache);
            }
        });
        if (logger.isDebugEnabled()) {
            logger.debug("Get group from dn {} in {} ms", dn, System.currentTimeMillis() - startTime);
        }

        return groupCacheEntry;
    }

    private LDAPGroupCacheEntry getAndCacheGroupEntry(GroupNameClassPairCallbackHandler nameClassPairCallbackHandler, boolean cache) {
        LDAPGroupCacheEntry ldapGroupCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
        if (ldapGroupCacheEntry != null) {
            if (cache) {
                ldapCacheManager.cacheGroup(getKey(), ldapGroupCacheEntry);
            }
            return ldapGroupCacheEntry;
        }
        return null;
    }

    /**
     * Retrieve the cache entry for a given dn, if not found create a new one, and cache it if the param "cache" set to true
     *
     * @param dn
     * @param cache
     * @return
     */
    private LDAPUserCacheEntry getUserCacheEntryByDN(final String dn, final boolean cache) {

        final List<String> userAttrs = getUserAttributes();
        long startTime = System.currentTimeMillis();
        final LDAPUserCacheEntry userCacheEntry = ldapTemplateWrapper.execute(new BaseLdapActionCallback<LDAPUserCacheEntry>(getExternalUserGroupService(), getKey()) {

            @Override
            public LDAPUserCacheEntry doInLdap(LdapTemplate ldapTemplate) {
                final UserNameClassPairCallbackHandler nameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(null);

                ldapTemplate.search(applyPredefinedUserFilter(query().base(dn)
                        .attributes(userAttrs.toArray(new String[userAttrs.size()]))
                        .searchScope(SearchScope.OBJECT)
                        .where(OBJECTCLASS_ATTRIBUTE).is(userConfig.getSearchObjectclass())),
                        nameClassPairCallbackHandler);
                if (nameClassPairCallbackHandler.getCacheEntry() != null) {
                    LDAPUserCacheEntry ldapUserCacheEntry = nameClassPairCallbackHandler.getCacheEntry();
                    if (cache) {
                        ldapCacheManager.cacheUser(getKey(), ldapUserCacheEntry);
                    }
                    return ldapUserCacheEntry;
                }
                return null;
            }
        });
        if (logger.isDebugEnabled()) {
            logger.debug("Get user from dn {} in {} ms", dn, System.currentTimeMillis() - startTime);
        }

        return userCacheEntry;
    }

    /**
     * Retrieve the search attribute from a dn. If the dn does'nt contains the search attribute null is returned
     *
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
            if (nameClassPair instanceof SearchResult) {
                SearchResult searchResult = (SearchResult) nameClassPair;
                cacheEntry = attributesToUserCacheEntry(searchResult.getAttributes(), cacheEntry);
                if (cacheEntry != null) {
                    cacheEntry.setDn(searchResult.getNameInNamespace());
                }
            } else {
                logger.error("Unexpected NameClassPair {} in {}", nameClassPair, getClass().getName());
            }
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
            if (nameClassPair instanceof SearchResult) {
                SearchResult searchResult = (SearchResult) nameClassPair;
                cacheEntry = attributesToGroupCacheEntry(searchResult.getAttributes(), cacheEntry);
                cacheEntry.setDynamic(isDynamic);
                if (isDynamic && searchResult.getAttributes().get(groupConfig.getDynamicMembersAttribute()) != null) {
                    cacheEntry.setDynamicMembersURL(searchResult.getAttributes().get(groupConfig.getDynamicMembersAttribute()).get().toString());
                }
                cacheEntry.setDn(searchResult.getNameInNamespace());
            } else {
                logger.error("Unexpected NameClassPair {} in {}", nameClassPair, getClass().getName());
            }
        }
    }

    /**
     * Callback handler for users, retrieve the list of usernames
     */
    private class UsersNameClassPairCallbackHandler implements NameClassPairCallbackHandler {

        private List<String> names = new ArrayList<>();

        public List<String> getNames() {
            return names;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            if (nameClassPair instanceof SearchResult) {
                SearchResult searchResult = (SearchResult) nameClassPair;
                LDAPUserCacheEntry cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                if (cacheEntry == null || cacheEntry.getExist() ==  null || !cacheEntry.getExist()) {
                    UserNameClassPairCallbackHandler nameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(cacheEntry);
                    nameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
                    cacheEntry = nameClassPairCallbackHandler.getCacheEntry();
                    if (cacheEntry != null) {
                        ldapCacheManager.cacheUser(getKey(), cacheEntry);
                    }
                }
                if (cacheEntry != null) {
                    names.add(cacheEntry.getName());
                }
            } else {
                logger.error("Unexpected NameClassPair {} in {}", nameClassPair, getClass().getName());
            }
        }
    }

    /**
     * Callback handler for groups, retrieve the list of groupnames
     */
    private class GroupsNameClassPairCallbackHandler implements NameClassPairCallbackHandler {

        private List<String> names = new LinkedList<>();
        private boolean isDynamic;

        public List<String> getNames() {
            return names;
        }

        private GroupsNameClassPairCallbackHandler(boolean isDynamic) {
            this.isDynamic = isDynamic;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {
            if (nameClassPair instanceof SearchResult) {
                SearchResult searchResult = (SearchResult) nameClassPair;
                LDAPGroupCacheEntry cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                if (cacheEntry == null || cacheEntry.getExist() == null || !cacheEntry.getExist().booleanValue()) {
                    GroupNameClassPairCallbackHandler nameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(cacheEntry, isDynamic);
                    nameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
                    cacheEntry = nameClassPairCallbackHandler.getCacheEntry();
                    if (cacheEntry != null) {
                        ldapCacheManager.cacheGroup(getKey(), cacheEntry);
                    }
                }
                if (cacheEntry != null) {
                    names.add(cacheEntry.getName());
                }
            } else {
                logger.error("Unexpected NameClassPair {} in {}", nameClassPair, getClass().getName());
            }
        }
    }

    /**
     * Calback handler for dynamic members, retrieve the list of members
     */
    private class DynMembersNameClassPairCallbackHandler implements NameClassPairCallbackHandler {

        private List<Member> members = Lists.newArrayList();

        public List<Member> getMembers() {
            return members;
        }

        @Override
        public void handleNameClassPair(NameClassPair nameClassPair) throws NamingException {

            if (nameClassPair instanceof SearchResult) {

                SearchResult searchResult = (SearchResult) nameClassPair;

                // try to know if we deal with a group or a user
                Boolean isUser = guessUserOrGroupFromDN(searchResult.getNameInNamespace());

                // try to retrieve the object from the cache
                LDAPAbstractCacheEntry cacheEntry;
                if (isUser != null) {
                    if (isUser) {
                        cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                    } else {
                        cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                    }
                } else {
                    // look in all cache
                    cacheEntry = ldapCacheManager.getUserCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                    if (cacheEntry == null) {
                        cacheEntry = ldapCacheManager.getGroupCacheEntryByDn(getKey(), searchResult.getNameInNamespace());
                        isUser = cacheEntry != null ? false : null;
                    } else {
                        isUser = true;
                    }
                }
                if (cacheEntry != null) {
                    if (isUser) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Dynamic member {} retrieved from cache and resolved as a user", searchResult.getNameInNamespace());
                        }
                        members.add(new Member(cacheEntry.getName(), Member.MemberType.USER));
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Dynamic member {} retrieved from cache and resolved as a group", searchResult.getNameInNamespace());
                        }
                        members.add(new Member(cacheEntry.getName(), Member.MemberType.GROUP));
                    }
                }

                // try the objectclass
                Boolean isDynamic = false;
                searchResult.getAttributes().get(OBJECTCLASS_ATTRIBUTE).getAll();
                List<String> objectclasses = new ArrayList<>();
                LdapUtils.collectAttributeValues(searchResult.getAttributes(), OBJECTCLASS_ATTRIBUTE, objectclasses, String.class);
                if (objectclasses.contains(userConfig.getSearchObjectclass())) {
                    isUser = true;
                } else if (objectclasses.contains(groupConfig.getSearchObjectclass())) {
                    isUser = false;
                } else if (groupConfig.isDynamicEnabled() && objectclasses.contains(groupConfig.getDynamicSearchObjectclass())) {
                    isUser = false;
                    isDynamic = true;
                }
                if (isUser != null) {
                    if (isUser) {
                        handleUserNameClassPair(nameClassPair, searchResult);
                    } else {
                        handleGroupNameClassPair(nameClassPair, searchResult, isDynamic);
                    }
                    return;
                }

                // try to guess the type on attributes present in the searchresult
                List<String> searchResultsAttr = new ArrayList<>();
                NamingEnumeration<String> attrs = searchResult.getAttributes().getIDs();
                while (attrs.hasMore()) {
                    searchResultsAttr.add(attrs.next());
                }
                List<String> commonUserAttrs = getCommonAttributes(searchResultsAttr, getUserAttributes());
                List<String> commonGroupAttrs = getCommonAttributes(searchResultsAttr, getGroupAttributes(isDynamic));
                if (commonUserAttrs.contains(userConfig.getUidSearchAttribute()) && commonUserAttrs.size() > commonGroupAttrs.size()) {
                    handleUserNameClassPair(nameClassPair, searchResult);
                    return;
                } else if (commonGroupAttrs.contains(groupConfig.getSearchAttribute())) {
                    handleGroupNameClassPair(nameClassPair, searchResult, false);
                    return;
                }

                // type not resolved
                logger.warn("Dynamic member: {} not resolved as a user or a group", searchResult.getNameInNamespace());
            } else {
                logger.error("Unexpected NameClassPair {} in {}", nameClassPair, getClass().getName());
            }
        }

        private void handleGroupNameClassPair(NameClassPair nameClassPair, SearchResult searchResult, Boolean isDynamic) throws NamingException {
            GroupNameClassPairCallbackHandler groupNameClassPairCallbackHandler = new GroupNameClassPairCallbackHandler(null, isDynamic);
            groupNameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
            LDAPGroupCacheEntry groupCacheEntry = groupNameClassPairCallbackHandler.getCacheEntry();
            ldapCacheManager.cacheGroup(getKey(), groupCacheEntry);
            members.add(new Member(groupCacheEntry.getName(), Member.MemberType.GROUP));
            if (logger.isDebugEnabled()) {
                logger.debug("Dynamic member {} resolved as a {}", searchResult.getNameInNamespace(), isDynamic ? " dynamic group" : " group");
            }
        }

        private void handleUserNameClassPair(NameClassPair nameClassPair, SearchResult searchResult) throws NamingException {
            UserNameClassPairCallbackHandler userNameClassPairCallbackHandler = new UserNameClassPairCallbackHandler(null);
            userNameClassPairCallbackHandler.handleNameClassPair(nameClassPair);
            LDAPUserCacheEntry userCacheEntry = userNameClassPairCallbackHandler.getCacheEntry();
            if (userCacheEntry != null) {
                ldapCacheManager.cacheUser(getKey(), userCacheEntry);
                members.add(new Member(userCacheEntry.getName(), Member.MemberType.USER));
                logger.debug("Dynamic member {} resolved as a user", searchResult.getNameInNamespace());
            }
        }
    }

    /**
     * Populate the given cache entry or create new one if the given is null with the LDAP attributes
     *
     * @param attrs
     * @param userCacheEntry
     * @return
     * @throws NamingException
     */
    private LDAPUserCacheEntry attributesToUserCacheEntry(Attributes attrs, LDAPUserCacheEntry userCacheEntry) throws NamingException {
        Attribute uidAttr = attrs.get(userConfig.getUidSearchAttribute());
        if (uidAttr == null) {
            logger.warn("LDAP user entry is missing the required {} attribute. Skipping user. Available attributes: {}",
                    userConfig.getUidSearchAttribute(), attrs);
            return null;
        }
        String userId = (String) uidAttr.get();
        JahiaUser jahiaUser = new JahiaUserImpl(encode(userId), null, attributesToJahiaProperties(attrs, true), getKey(), null);
        if (userCacheEntry == null) {
            userCacheEntry = new LDAPUserCacheEntry(userId);
        }
        userCacheEntry.setExist(true);
        userCacheEntry.setUser(jahiaUser);
        return userCacheEntry;
    }

    /**
     * Populate the given cache entry or create new one if the given is null with the LDAP attributes
     *
     * @param attrs
     * @param groupCacheEntry
     * @return
     * @throws NamingException
     */
    private LDAPGroupCacheEntry attributesToGroupCacheEntry(Attributes attrs, LDAPGroupCacheEntry groupCacheEntry) throws NamingException {
        String groupId = (String) attrs.get(groupConfig.getSearchAttribute()).get();
        JahiaGroup jahiaGroup = new JahiaGroupImpl(encode(groupId), null, null, attributesToJahiaProperties(attrs, false));

        if (groupCacheEntry == null) {
            groupCacheEntry = new LDAPGroupCacheEntry(jahiaGroup.getName());
        }
        groupCacheEntry.setExist(true);
        groupCacheEntry.setGroup(jahiaGroup);
        return groupCacheEntry;
    }

    /**
     * Map ldap attributes to jahia properties
     *
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
            } catch (NamingException e) {
                logger.error("Error reading LDAP attribute: {}", ldapAttribute);
            }
        }
        return props;
    }

    /**
     * Build a user query, that use the searchCriteria from jahia forms
     *
     * If any of the searchCriteria doesn't map to LDAP properties,
     * then it returns an empty query (query that returns 0 results)
     *
     * @param searchCriteria
     * @return
     */
    private ContainerCriteria buildUserQuery(Properties searchCriteria) {
        // transform jnt:user props to ldap props
        Properties ldapfilters = mapJahiaPropertiesToLDAP(searchCriteria, userConfig.getAttributesMapper());
        if (ldapfilters == null) {
            return null;
        }

        List<String> attributesToRetrieve = getUserAttributes();
        ContainerCriteria query = query().base(userConfig.getUidSearchName())
                .attributes(attributesToRetrieve.toArray(new String[attributesToRetrieve.size()]))
                .countLimit((int) userConfig.getSearchCountlimit())
                .where(OBJECTCLASS_ATTRIBUTE).is(StringUtils.defaultString(userConfig.getSearchObjectclass(), "*"));

        applyPredefinedUserFilter(query);

        // define and / or operator
        boolean orOp = isOrOperator(ldapfilters, searchCriteria);

        // process the user specific filters
        ContainerCriteria filterQuery = getQueryFilters(ldapfilters, userConfig, orOp);

        if (filterQuery != null) {
            query.and(filterQuery);
        }

        return query;
    }

    private ContainerCriteria createContainerCriteria(String filter) {
        try {
            return (ContainerCriteria) FieldUtils.readField(query().filter(filter), "rootContainer", true);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private ContainerCriteria getGroupSearchFilterCriteria() {
        if (groupSearchFilterCriteria == null) {
            String filter = groupConfig.getSearchFilter();
            if (filter != null) {
                groupSearchFilterCriteria = createContainerCriteria(filter);
                logger.info("Using pre-defined filter for group search: {}", filter);
            }
        }

        return groupSearchFilterCriteria;
    }

    private ContainerCriteria getUserSearchFilterCriteria() {
        if (userSearchFilterCriteria == null) {
            String filter = userConfig.getSearchFilter();
            if (filter != null) {
                userSearchFilterCriteria = createContainerCriteria(filter);
                logger.info("Using pre-defined filter for user search: {}", filter);
            }
        }

        return userSearchFilterCriteria;
    }

    private ContainerCriteria getGroupQuery(Properties searchCriteria, boolean isDynamic) {
        if (!searchCriteria.isEmpty()) {
            return buildGroupQuery(searchCriteria, isDynamic);
        }

        if (isDynamic) {
            return getSearchGroupDynamicCriteria(searchCriteria);
        } else {
            return getSearchGroupCriteria(searchCriteria);
        }
    }

    private void flushGroupQuery() {
        searchGroupCriteria = null;
        searchGroupDynamicCriteria = null;
        groupSearchFilterCriteria = null;
    }

    /**
     * Build a group query based on search criteria
     *
     * @param searchCriteria
     * @param isDynamic
     * @return
     */
    private ContainerCriteria buildGroupQuery(Properties searchCriteria, boolean isDynamic) {
        // transform jnt:group props to ldap props
        Properties ldapfilters = mapJahiaPropertiesToLDAP(searchCriteria, groupConfig.getAttributesMapper());
        if (ldapfilters == null) {
            return null;
        }

        List<String> attributesToRetrieve = getGroupAttributes(isDynamic);
        if (isDynamic) {
            attributesToRetrieve.add(groupConfig.getDynamicMembersAttribute());
        }

        ContainerCriteria query = query().base(groupConfig.getSearchName())
                .attributes(attributesToRetrieve.toArray(new String[attributesToRetrieve.size()]))
                .countLimit((int) groupConfig.getSearchCountlimit())
                .where(OBJECTCLASS_ATTRIBUTE).is(isDynamic ? groupConfig.getDynamicSearchObjectclass() : groupConfig.getSearchObjectclass());

        applyPredefinedGroupFilter(query);

        // define and / or operator
        boolean orOp = isOrOperator(ldapfilters, searchCriteria);

        // process the user specific filters
        ContainerCriteria filterQuery = getQueryFilters(ldapfilters, groupConfig, orOp);

        if (filterQuery != null) {
            query.and(filterQuery);
        }

        return query;
    }

    private static boolean isOrOperator(Properties ldapfilters, Properties searchCriteria) {
        if (ldapfilters != null && ldapfilters.size() > 1) {
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
     *
     * @param ldapfilters
     * @param config
     * @param isOrOperator
     * @return
     */
    private ContainerCriteria getQueryFilters(Properties ldapfilters, AbstractConfig config, boolean isOrOperator) {
        ContainerCriteria filterQuery = null;
        if (ldapfilters == null) {
            return filterQuery;
        }

        if (ldapfilters.containsKey("*")) {
            // Search on all wildcards attributes
            String filterValue = ldapfilters.getProperty("*");
            if (CollectionUtils.isNotEmpty(config.getSearchWildcardsAttributes())) {
                for (String wildcardAttribute : config.getSearchWildcardsAttributes()) {
                    if (filterQuery == null) {
                        filterQuery = query().where(wildcardAttribute).like(filterValue);
                    } else {
                        addCriteriaToQuery(filterQuery, true, wildcardAttribute).like(filterValue);
                    }
                }
            }
        } else {
            // consider the attributes
            Iterator<?> filterKeys = ldapfilters.keySet().iterator();
            while (filterKeys.hasNext()) {
                String filterName = (String) filterKeys.next();
                String filterValue = ldapfilters.getProperty(filterName);

                if (filterQuery == null) {
                    filterQuery = query().where(filterName).like(filterValue);
                } else {
                    addCriteriaToQuery(filterQuery, isOrOperator, filterName).like(filterValue);
                }
            }
        }
        return filterQuery;
    }

    private ConditionCriteria addCriteriaToQuery(ContainerCriteria query, boolean isOr, String attribute) {
        if (isOr) {
            return query.or(attribute);
        } else {
            return query.and(attribute);
        }
    }

    /**
     * Map jahia properties to ldap properties
     *
     * @param searchCriteria
     * @param configProperties
     * @return
     */
    private Properties mapJahiaPropertiesToLDAP(Properties searchCriteria, Map<String, String> configProperties) {

        if (searchCriteria.isEmpty()) {
            return searchCriteria;
        }

        Properties p = new Properties();
        if (searchCriteria.containsKey("*")) {
            p.setProperty("*", searchCriteria.getProperty("*"));
            if (searchCriteria.size() == 1) {
                return p;
            }
        }

        for (Map.Entry<Object, Object> entry : searchCriteria.entrySet()) {
            if (configProperties.containsKey(entry.getKey())) {
                p.setProperty(configProperties.get(entry.getKey()), (String) entry.getValue());
            } else if (!entry.getKey().equals("*") && !entry.getKey().equals(JahiaUserManagerService.MULTI_CRITERIA_SEARCH_OPERATION)) {
                return null;
            }
        }

        return p;
    }

    /**
     * Try to guess if the given dn is a user or a group
     *
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
     *
     * @return
     */
    private List<String> getUserAttributes() {
        List<String> attrs = new ArrayList<>(userConfig.getAttributesMapper().values());
        attrs.add(userConfig.getUidSearchAttribute());
        return attrs;
    }

    /**
     * get group ldap attributes that need to be return from the ldap
     *
     * @return
     */
    private List<String> getGroupAttributes(boolean isDynamic) {
        List<String> attrs = new ArrayList<>(groupConfig.getAttributesMapper().values());
        attrs.add(groupConfig.getSearchAttribute());
        if (isDynamic) {
            attrs.add(groupConfig.getDynamicMembersAttribute());
        }
        return attrs;
    }

    /**
     * Construct a list that contain only the elements also contains from the other list
     *
     * @param first
     * @param second
     * @return
     */
    private List<String> getCommonAttributes(List<String> first, List<String> second) {
        List<String> commons = new ArrayList<>(first);
        commons.retainAll(second);
        return commons;
    }

    public void setLdapTemplateWrapper(LdapTemplateWrapper ldapTemplateWrapper) {
        this.ldapTemplateWrapper = ldapTemplateWrapper;
    }

    public LdapTemplateWrapper getLdapTemplateWrapper() {
        if (userConfig.isShareable()) {
            return ldapTemplateWrapper;
        }
        return null;
    }

    public void setContextSource(LdapContextSource contextSource) {
        this.contextSource = contextSource;
    }

    @Override
    protected String getSiteKey() {
        return userConfig.getTargetSite();
    }

    public void setLdapCacheManager(LDAPCacheManager ldapCacheManager) {
        this.ldapCacheManager = ldapCacheManager;
    }

    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
        userSearchFilterCriteria = null;
    }

    // This only should be invoked during a period of provider inactivity, so that flushing group queries does not interfere with ongoing requests serving different threads simultaneously.
    public void setGroupConfig(GroupConfig groupConfig) {
        this.groupConfig = groupConfig;
        flushGroupQuery();
    }

    public void setDistinctBase(boolean distinctBase) {
        this.distinctBase = distinctBase;
    }

    public void setMaxLdapTimeoutCountBeforeDisconnect(int maxLdapTimeoutCountBeforeDisconnect) {
        this.maxLdapTimeoutCountBeforeDisconnect = maxLdapTimeoutCountBeforeDisconnect;
    }

    @Override
    public boolean supportsGroups() {
        return groupConfig.isMinimalSettingsOk();
    }

    @Override
    public String toString() {
        return "LDAPUserGroupProvider{" + "getKey()='" + getKey() + '\'' + '}';
    }

    private ContainerCriteria getSearchGroupCriteria(Properties searchCriteria) {
        // Thread-safe lazy loading, using double-checked locking pattern
        // see https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java
        ContainerCriteria criteria = searchGroupCriteria;
        if (criteria == null) {
            synchronized (this) {
                criteria = searchGroupCriteria;
                if (criteria == null) {
                    searchGroupCriteria = criteria = buildGroupQuery(searchCriteria, false);
                }
            }
        }
        return criteria;
    }

    private ContainerCriteria getSearchGroupDynamicCriteria(Properties searchCriteria) {
        // Thread-safe lazy loading, using double-checked locking pattern
        // see https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java
        ContainerCriteria criteria = searchGroupDynamicCriteria;
        if (criteria == null) {
            synchronized (this) {
                criteria = searchGroupDynamicCriteria;
                if (criteria == null) {
                    searchGroupDynamicCriteria = criteria = buildGroupQuery(searchCriteria, true);
                }
            }
        }
        return criteria;
    }

    /**
     * Base LDAP template action callback that unmounts the LDAP provider in case of communication issue with the LDAP server
     * using the onError method.
     * Feel free to use it, implementing at least the doInLdap to wrap the call to the ldapTemplate object.
     *
     * @author kevan
     */
    public abstract class BaseLdapActionCallback<T> implements LdapTemplateCallback<T> {

        private final ExternalUserGroupService externalUserGroupService;
        private final String key;

        protected BaseLdapActionCallback(ExternalUserGroupService externalUserGroupService, String key) {
            this.externalUserGroupService = externalUserGroupService;
            this.key = key;
        }

        @Override
        public void onSuccess() {
            timeoutCount.set(0);
        }

        @Override
        public T onError(Exception e)  {
            final Throwable cause = e.getCause();
            logger.error("An error occurred while communicating with the LDAP server " + key, e);
            if (cause instanceof javax.naming.CommunicationException || cause instanceof javax.naming.NamingException || cause instanceof CommunicationException || cause instanceof ServiceUnavailableException || cause instanceof InsufficientResourcesException) {
                if (timeoutCount.incrementAndGet() >= maxLdapTimeoutCountBeforeDisconnect) {
                    externalUserGroupService.setMountStatus(key, JCRMountPointNode.MountStatus.waiting, cause.getMessage());
                }
            } else {
                externalUserGroupService.setMountStatus(key, JCRMountPointNode.MountStatus.error, e.getMessage());
            }
            return null;
        }
    }

    private String decode(String name) {
        return Text.unescapeIllegalJcrChars(name);
    }

    private String encode(String value) {
        return Text.escapeIllegalJcrChars(value);
    }
}
