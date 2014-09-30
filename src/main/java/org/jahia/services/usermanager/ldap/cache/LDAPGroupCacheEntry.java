package org.jahia.services.usermanager.ldap.cache;

import org.jahia.modules.external.users.Member;

import java.io.Serializable;
import java.util.List;

/**
 * @author kevan
 */
public class LDAPGroupCacheEntry extends LDAPAbstractCacheEntry implements Serializable{
    private static final long serialVersionUID = -3585067276227107907L;

    private List<Member> members = null;

    public LDAPGroupCacheEntry(String name) {
        setName(name);
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }
}
