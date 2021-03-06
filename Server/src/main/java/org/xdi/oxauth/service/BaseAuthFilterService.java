/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.jboss.seam.log.Log;
import org.jboss.seam.log.Logging;
import org.xdi.ldap.model.LdapDummyEntry;
import org.xdi.oxauth.model.config.BaseFilter;
import org.xdi.util.ArrayHelper;
import org.xdi.util.StringHelper;

import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;

/**
 * @author Yuriy Movchan
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 02/08/2012
 */

public abstract class BaseAuthFilterService {

    public static class AuthenticationFilterWithParameters {

        private BaseFilter authenticationFilter;
        private List<String> variableNames;
        private List<AuthenticationFilterService.IndexedParameter> indexedVariables;

        public AuthenticationFilterWithParameters(BaseFilter authenticationFilter, List<String> variableNames, List<AuthenticationFilterService.IndexedParameter> indexedVariables) {
            this.authenticationFilter = authenticationFilter;
            this.variableNames = variableNames;
            this.indexedVariables = indexedVariables;
        }

        public BaseFilter getAuthenticationFilter() {
            return authenticationFilter;
        }

        public void setAuthenticationFilter(BaseFilter authenticationFilter) {
            this.authenticationFilter = authenticationFilter;
        }

        public List<String> getVariableNames() {
            return variableNames;
        }

        public void setVariableNames(List<String> variableNames) {
            this.variableNames = variableNames;
        }

        public List<AuthenticationFilterService.IndexedParameter> getIndexedVariables() {
            return indexedVariables;
        }

        public void setIndexedVariables(List<AuthenticationFilterService.IndexedParameter> indexedVariables) {
            this.indexedVariables = indexedVariables;
        }

        public String toString() {
            return String.format("AutheticationFilterWithParameters [authenticationFilter=%s, variableNames=%s, indexedVariables=%s]",
                    authenticationFilter, variableNames, indexedVariables);
        }

    }

    public static class IndexedParameter {

        private String paramName;
        private String paramIndex;

        public IndexedParameter(String paramName, String paramIndex) {
            this.paramName = paramName;
            this.paramIndex = paramIndex;
        }

        public String getParamName() {
            return paramName;
        }

        public void setParamName(String paramName) {
            this.paramName = paramName;
        }

        public String getParamIndex() {
            return paramIndex;
        }

        public void setParamIndex(String paramIndex) {
            this.paramIndex = paramIndex;
        }

        public String toString() {
            return String.format("IndexedParameter [paramName=%s, paramIndex=%s]", paramName, paramIndex);
        }
    }

    public static final Pattern PARAM_VALUE_PATTERN = Pattern.compile("([\\w]+)[\\s]*\\=[\\*\\s]*(\\{[\\s]*[\\d]+[\\s]*\\})[\\*\\s]*");

    private static final Log LOG = Logging.getLog(BaseAuthFilterService.class);

    private boolean enabled;
    private boolean filterAttributes = true;

    private List<AuthenticationFilterWithParameters> filterWithParameters;

    public void init(List<? extends BaseFilter> p_filterList, boolean p_enabled, boolean p_filterAttributes) {
        this.enabled = p_enabled;
        this.filterWithParameters = prepareAuthenticationFilterWithParameters(p_filterList);
        this.filterAttributes = p_filterAttributes;
    }

    private List<AuthenticationFilterWithParameters> prepareAuthenticationFilterWithParameters(List<? extends BaseFilter> p_filterList) {
        final List<AuthenticationFilterWithParameters> tmpAuthenticationFilterWithParameters = new ArrayList<AuthenticationFilterWithParameters>();

        if (!this.enabled || p_filterList == null) {
            return tmpAuthenticationFilterWithParameters;
        }

        for (BaseFilter authenticationFilter : p_filterList) {
            if (Boolean.TRUE.equals(authenticationFilter.getBind()) && StringHelper.isEmpty(authenticationFilter.getBindPasswordAttribute())) {
                LOG.error("Skipping authentication filter:\n '{0}'\n. It should contains not empty bind-password-attribute attribute. ", authenticationFilter);
                continue;
            }

            List<String> variableNames = new ArrayList<String>();
            List<BaseAuthFilterService.IndexedParameter> indexedParameters = new ArrayList<BaseAuthFilterService.IndexedParameter>();

            Matcher matcher = BaseAuthFilterService.PARAM_VALUE_PATTERN.matcher(authenticationFilter.getFilter());
            while (matcher.find()) {
                String paramName = normalizeAttributeName(matcher.group(1));
                String paramIndex = matcher.group(2);

                variableNames.add(paramName);
                indexedParameters.add(new BaseAuthFilterService.IndexedParameter(paramName, paramIndex));
            }

            AuthenticationFilterWithParameters tmpAutheticationFilterWithParameter = new AuthenticationFilterWithParameters(authenticationFilter, variableNames, indexedParameters);
            tmpAuthenticationFilterWithParameters.add(tmpAutheticationFilterWithParameter);

            LOG.debug("Authentication filter with parameters: '{0}'. ", tmpAutheticationFilterWithParameter);
        }

        return tmpAuthenticationFilterWithParameters;
    }

    public static List<AuthenticationFilterWithParameters> getAllowedAuthenticationFilters(Collection<?> attributeNames, List<AuthenticationFilterWithParameters> p_filterList) {
        List<AuthenticationFilterWithParameters> tmpAuthenticationFilterWithParameters = new ArrayList<AuthenticationFilterWithParameters>();
        if (attributeNames == null) {
            return tmpAuthenticationFilterWithParameters;
        }

        Set<String> normalizedAttributeNames = new HashSet<String>();
        for (Object attributeName : attributeNames) {
            normalizedAttributeNames.add(normalizeAttributeName(attributeName.toString()));
        }

        for (AuthenticationFilterWithParameters autheticationFilterWithParameters : p_filterList) {
            if (normalizedAttributeNames.containsAll(autheticationFilterWithParameters.getVariableNames())) {
                tmpAuthenticationFilterWithParameters.add(autheticationFilterWithParameters);
            }
        }

        return tmpAuthenticationFilterWithParameters;
    }

    public static Map<String, String> normalizeAttributeMap(Map<?, ?> attributeValues) {
        Map<String, String> normalizedAttributeValues = new HashMap<String, String>();
        for (Map.Entry<?, ?> attributeValueEntry : attributeValues.entrySet()) {
            String attributeValue = null;

            Object attributeValueEntryValue = attributeValueEntry.getValue();
            if (attributeValueEntryValue instanceof String[]) {
                if (ArrayHelper.isNotEmpty((String[]) attributeValueEntryValue)) {
                    attributeValue = ((String[]) attributeValueEntryValue)[0];
                }
            } else if (attributeValueEntryValue instanceof String) {
                attributeValue = (String) attributeValueEntryValue;
            } else if (attributeValueEntryValue != null) {
                attributeValue = attributeValueEntryValue.toString();
            }

            if (attributeValue != null) {
                normalizedAttributeValues.put(normalizeAttributeName(attributeValueEntry.getKey().toString()), attributeValue);
            }
        }
        return normalizedAttributeValues;
    }

    public static String buildFilter(AuthenticationFilterWithParameters authenticationFilterWithParameters, Map<String, String> p_normalizedAttributeValues) {
        String filter = authenticationFilterWithParameters.getAuthenticationFilter().getFilter();
        for (IndexedParameter indexedParameter : authenticationFilterWithParameters.getIndexedVariables()) {
            String attributeValue = p_normalizedAttributeValues.get(indexedParameter.getParamName());
            filter = filter.replace(indexedParameter.getParamIndex(), attributeValue);
        }
        return filter;
    }

    public static String loadEntryDN(LdapEntryManager p_manager, AuthenticationFilterWithParameters authenticationFilterWithParameters, Map<String, String> normalizedAttributeValues) {
        final String filter = buildFilter(authenticationFilterWithParameters, normalizedAttributeValues);

    	Filter ldapFilter;
		try {
			ldapFilter = Filter.create(filter);
		} catch (LDAPException ex) {
			LOG.error("Failed to create Ldap filter: '{0}'", ex, filter);
			return null;
		}

    	List<LdapDummyEntry> foundEntries = p_manager.findEntries(authenticationFilterWithParameters.getAuthenticationFilter().getBaseDn(), LdapDummyEntry.class, new String[0], ldapFilter);

    	if (foundEntries.size() > 1) {
    		LOG.error("Found more than one entry by filter: '{0}'. Entries:\n", ldapFilter, foundEntries);
    		return null;
    	}

        if (!(foundEntries.size() == 1)) {
    		return null;
    	}

    	return foundEntries.get(0).getDn();
    }

    public String processAuthenticationFilters(Map<?, ?> attributeValues) {
    	if (attributeValues == null) {
    		return null;
    	}

    	final List<AuthenticationFilterWithParameters> allowedList = filterAttributes ?
                getAllowedAuthenticationFilters(attributeValues.keySet(), getFilterWithParameters()) :
                getFilterWithParameters();

    	for (AuthenticationFilterWithParameters allowed : allowedList) {
    		String resultDn = processAuthenticationFilter(allowed, attributeValues);
    		if (StringHelper.isNotEmpty(resultDn)) {
    			return resultDn;
    		}
    	}

    	return null;
    }

    public abstract String processAuthenticationFilter(AuthenticationFilterWithParameters p_allowed, Map<?,?> p_attributeValues);

    public List<AuthenticationFilterWithParameters> getFilterWithParameters() {
        return filterWithParameters;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean p_enabled) {
        enabled = p_enabled;
    }

    public boolean isFilterAttributes() {
        return filterAttributes;
    }

    public void setFilterAttributes(boolean p_filterAttributes) {
        filterAttributes = p_filterAttributes;
    }

    public static String normalizeAttributeName(String attributeName) {
        return StringHelper.toLowerCase(attributeName.trim());
    }
}
