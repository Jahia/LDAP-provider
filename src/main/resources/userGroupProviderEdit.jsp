<%@ page import="org.jahia.data.templates.JahiaTemplatesPackage" %>
<%@ page import="org.jahia.registries.ServicesRegistry" %>
<%@ page import="org.jahia.services.render.Resource" %>
<%@ page import="org.jahia.services.templates.JahiaTemplateManagerService" %>
<%@ page import="org.jahia.utils.i18n.ResourceBundles" %>
<%@ page import="javax.servlet.jsp.jstl.fmt.LocalizationContext" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.jahia.bin.ActionResult" %>
<%@ page import="org.osgi.service.cm.Configuration" %>
<%@ page import="java.util.*" %>
<%@ page import="org.jahia.services.usermanager.ldap.JahiaLDAPConfig" %>
<%@ page import="org.jahia.services.usermanager.ldap.JahiaLDAPConfigFactory" %>
<%@ page import="org.osgi.service.cm.ConfigurationAdmin" %>
<%@ page import="org.jahia.osgi.BundleUtils" %>
<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="query" uri="http://www.jahia.org/tags/queryLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%--@elvariable id="providerKey" type="java.lang.String"--%>


<%
    List<String> defaultProperties = Arrays.asList("target.site", "url", "public.bind.dn", "public.bind.password", "user.uid.search.name", "group.search.name");

    Resource currentResource = (Resource) pageContext.findAttribute("currentResource");
    JahiaTemplatesPackage ldap = ServicesRegistry.getInstance().getJahiaTemplateManagerService().getTemplatePackageById("ldap");
    ResourceBundle rb = ResourceBundles.get(ldap, currentResource.getLocale());
    LocalizationContext ctx = new LocalizationContext(rb);
    pageContext.setAttribute("bundle", ctx);
    String providerKey = (String) pageContext.findAttribute("providerKey");
    Map<String, String> previousProperties = (Map<String, String>) pageContext.findAttribute("ldapProperties");
    pageContext.setAttribute("defaultProperties", defaultProperties);

    JahiaLDAPConfigFactory jahiaLDAPConfigFactory = BundleUtils.getOsgiService(JahiaLDAPConfigFactory.class, null);
    ConfigurationAdmin configurationAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);

    Dictionary<String, Object> properties = null;
    if (providerKey != null) {
        String pid = jahiaLDAPConfigFactory.getConfigPID(providerKey);
        Configuration configuration = configurationAdmin.getConfiguration(pid);
        properties = configuration.getProperties();
    }

    Map<String, String> res = new LinkedHashMap<String, String>();
    for (String key : defaultProperties) {
        if (previousProperties != null && previousProperties.containsKey(key)) {
            res.put(key, previousProperties.get(key));
        } else if (properties != null && properties.get(key) != null) {
            res.put(key, (String) properties.get(key));
        } else {
            res.put(key, "");
        }
    }
    Map<String, String> sorted = new TreeMap<String, String>();

    if (previousProperties != null) {
        for (String key : previousProperties.keySet()) {
            if (!defaultProperties.contains(key) && !JahiaLDAPConfig.LDAP_PROVIDER_KEY_PROP.equals(key)) {
                sorted.put(key, previousProperties.get(key));
            }
        }
    }

    if (properties != null) {
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (!sorted.containsKey(key) && !defaultProperties.contains(key) && !key.startsWith("service.") && !key.startsWith("felix.")
                    && !JahiaLDAPConfig.LDAP_PROVIDER_KEY_PROP.equals(key)) {
                sorted.put(key, (String) properties.get(key));
            }
        }
    }
    res.putAll(sorted);

    pageContext.setAttribute("ldapProperties", res);

%>

<c:choose>
    <c:when test="${sessionScope['jahia.ui.theme']=='jahia-anthracite'}">
        <%@include file="LdapForm.settingsBootstrap3GoogleMaterialStyle.jspf"%>
    </c:when>
    <c:otherwise>
        <%@include file="LdapForm.jspf"%>
    </c:otherwise>
</c:choose>
