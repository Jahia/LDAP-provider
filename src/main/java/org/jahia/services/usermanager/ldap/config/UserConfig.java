/*
 * Copyright (C) 2002-2022 Jahia Solutions Group SA. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jahia.services.usermanager.ldap.config;

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;

/**
 * User specific config provide by the ldap config file
 * @author kevan
 */
public class UserConfig extends AbstractConfig{
    private String uidSearchName;
    private String uidSearchAttribute = "cn";
    private boolean shareable = false;

    public UserConfig() {
    }

    public void handleDefaults() {
        if(StringUtils.isEmpty(getSearchObjectclass())){
            setSearchObjectclass("person");
        }
        if(getSearchWildcardsAttributes().isEmpty()){
            setSearchWildcardsAttributes(Sets.newHashSet("ou", "cn", "o", "c", "mail", "uid", "uniqueIdentifier", "givenName", "sn", "dn"));
        }
        if(getAttributesMapper().isEmpty()){
            getAttributesMapper().put("username", uidSearchAttribute);
            getAttributesMapper().put("j:firstName", "givenName");
            getAttributesMapper().put("j:lastName", "sn");
            getAttributesMapper().put("j:email", "mail");
            getAttributesMapper().put("j:organization", "o");
        }
    }

    public boolean isMinimalSettingsOk() {
        return StringUtils.isNotEmpty(getUrl()) && StringUtils.isNotEmpty(getUidSearchName());
    }

    public String getUidSearchName() {
        return uidSearchName;
    }

    public void setUidSearchName(String uidSearchName) {
        this.uidSearchName = uidSearchName;
    }

    public String getUidSearchAttribute() {
        return uidSearchAttribute;
    }

    public void setUidSearchAttribute(String uidSearchAttribute) {
        this.uidSearchAttribute = uidSearchAttribute;
    }
    
    public boolean isShareable() {
        return shareable;
    }

    public void setShareable(boolean shareable) {
        this.shareable = shareable;
    }
}
