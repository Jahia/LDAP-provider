package org.jahia.services.usermanager.ldap;

import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.ldap.cache.LDAPCacheManager;
import org.jahia.services.usermanager.ldap.cache.LDAPUserCacheEntry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.support.LdapContextSource;

import javax.naming.Name;
import javax.naming.directory.DirContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class LDAPUserGroupProviderVerifyPasswordTest {

    private static final String USER_NAME = "jdoe";
    private static final String USER_DN = "uid=jdoe,ou=people,dc=example,dc=org";
    private static final String PASSWORD = "s3cret";

    private LDAPCacheManager cacheManager;
    private LdapContextSource contextSource;
    private DirContext dirContext;
    private LDAPUserGroupProvider provider;

    @Before
    public void setUp() {
        LDAPUserCacheEntry userCacheEntry = new LDAPUserCacheEntry(USER_NAME);
        userCacheEntry.setExist(true);
        userCacheEntry.setDn(USER_DN);
        userCacheEntry.setUser(mock(JahiaUser.class));

        cacheManager = mock(LDAPCacheManager.class);
        when(cacheManager.getUserCacheEntryByName(any(), anyString())).thenReturn(userCacheEntry);

        dirContext = mock(DirContext.class);
        contextSource = mock(LdapContextSource.class);
        when(contextSource.getContext(anyString(), any())).thenReturn(dirContext);
        when(contextSource.getContext(USER_DN, "wrong")).thenThrow(new AuthenticationException(new javax.naming.AuthenticationException()));

        provider = new LDAPUserGroupProvider();
        provider.setLdapCacheManager(cacheManager);
        provider.setContextSource(contextSource);
    }

    @Test
    public void matchingPasswordIsAccepted() throws Exception {
        assertTrue(provider.verifyPassword(USER_NAME, PASSWORD));
        verify(dirContext).lookup(any(Name.class));
    }

    @Test
    public void wrongPasswordIsRefused() {
        assertFalse(provider.verifyPassword(USER_NAME, "wrong"));
    }

    @Test
    public void whitespacePasswordIsCheckedByTheDirectory() {
        assertTrue(provider.verifyPassword(USER_NAME, " "));
        verify(contextSource).getContext(USER_DN, " ");
    }

    @Test
    public void emptyOrNullPasswordIsRefusedWithoutContactingTheDirectory() {
        assertFalse(provider.verifyPassword(USER_NAME, ""));
        assertFalse(provider.verifyPassword(USER_NAME, null));
        verifyNoInteractions(cacheManager, contextSource);
    }
}
