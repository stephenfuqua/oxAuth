/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2015, Gluu
 */

package org.xdi.oxauth.model.fido.u2f.protocol;

import java.io.Serializable;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jboss.resteasy.annotations.providers.jaxb.IgnoreMediaTypes;
import org.xdi.oxauth.model.fido.u2f.exception.BadInputException;

/**
 * FIDO U2F device registration response
 *
 * @author Yuriy Movchan Date: 05/13/2015
 */
@IgnoreMediaTypes("application/*+json")
// try to ignore jettison as it's recommended here:
// http://docs.jboss.org/resteasy/docs/2.3.4.Final/userguide/html/json.html
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterResponse implements Serializable {

	private static final long serialVersionUID = -4192863815075074953L;

	/**
	 * base64 (raw registration response message)
	 */
	@JsonProperty
	private final String registrationData;

	/**
	 * base64(UTF8(client data))
	 */
	@JsonProperty
	private final String clientData;

	@JsonIgnore
	private transient ClientData clientDataRef;

	public RegisterResponse(@JsonProperty("registrationData") String registrationData, @JsonProperty("clientData") String clientData) throws BadInputException {
		this.registrationData = registrationData;
		this.clientData = clientData;
		this.clientDataRef = new ClientData(clientData);
	}

	public String getRegistrationData() {
		return registrationData;
	}

	public ClientData getClientData() {
		return clientDataRef;
	}

	@JsonIgnore
	public String getRequestId() {
		return getClientData().getChallenge();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("RegisterResponse [registrationData=").append(registrationData).append(", clientData=").append(clientData).append(", clientDataRef=")
				.append(clientDataRef).append("]");
		return builder.toString();
	}

}
