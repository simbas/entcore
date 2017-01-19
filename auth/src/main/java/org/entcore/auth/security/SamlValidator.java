/*
 * Copyright © WebServices pour l'Éducation, 2015
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.auth.security;

import org.joda.time.DateTime;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.SAMLVersion;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.core.*;
import org.opensaml.saml2.core.impl.*;
import org.opensaml.saml2.encryption.Decrypter;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml2.metadata.SingleLogoutService;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml2.metadata.provider.FilesystemMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.security.MetadataCredentialResolver;
import org.opensaml.security.MetadataCriteria;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.encryption.InlineEncryptedKeyResolver;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.security.CriteriaSet;
import org.opensaml.xml.security.credential.UsageType;
import org.opensaml.xml.security.criteria.EntityIDCriteria;
import org.opensaml.xml.security.criteria.UsageCriteria;
import org.opensaml.xml.security.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureTrustEngine;
import org.opensaml.xml.signature.impl.ExplicitKeySignatureTrustEngine;
import org.opensaml.xml.validation.ValidationException;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class SamlValidator extends BusModBase implements Handler<Message<JsonObject>> {

	private final Map<String, SignatureTrustEngine> signatureTrustEngineMap = new HashMap<>();
	private final Map<String, EntityDescriptor> entityDescriptorMap = new HashMap<>();
	private RSAPrivateKey privateKey;
	private String issuer;

	@Override
	public void start() {
		super.start();
		try {
			DefaultBootstrap.bootstrap();
			String path = config.getString("saml-metadata-folder");
			if (path == null || path.trim().isEmpty()) {
				logger.error("Metadata folder not found.");
				return;
			}
			issuer = config.getString("saml-issuer");
			if (issuer == null || issuer.trim().isEmpty()) {
				logger.error("Empty issuer");
				return;
			}
			for (String f : vertx.fileSystem().readDirSync(path)) {
				loadSignatureTrustEngine(f);
			}
			loadPrivateKey(config.getString("saml-private-key"));
			vertx.eventBus().registerLocalHandler("saml", this);
		} catch (ConfigurationException | MetadataProviderException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			logger.error("Error loading SamlValidator.", e);
		}
	}

	private void loadPrivateKey(String path) throws NoSuchAlgorithmException, InvalidKeySpecException {
		if (path != null && !path.trim().isEmpty() && vertx.fileSystem().existsSync(path)) {
			byte[] encodedPrivateKey = vertx.fileSystem().readFileSync(path).getBytes();
			PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
			privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec);
		}
	}

	@Override
	public void handle(Message<JsonObject> message) {
		final String action = message.body().getString("action", "");
		final String response = message.body().getString("response");
		final String idp = message.body().getString("IDP");
		if (!"generate-slo-request".equals(action) && !"generate-authn-request".equals(action) &&
				(response == null || response.trim().isEmpty())) {
			sendError(message, "invalid.response");
			return;
		}
		try {
			switch (action) {
				case "generate-authn-request" :
					sendOK(message, new JsonObject().putString("authn-request", generateAuthnRequest(idp)));
					break;
				case "validate-signature":
					sendOK(message, new JsonObject().putBoolean("valid", validateSignature(response)));
					break;
				case "decrypt-assertion":
					sendOK(message, new JsonObject().putString("assertion", decryptAssertion(response)));
					break;
				case "validate-signature-decrypt":
					final JsonObject res = new JsonObject();
					if (validateSignature(response)) {
						res.putBoolean("valid", true).putString("assertion", decryptAssertion(response));
					} else {
						res.putBoolean("valid", false).putString("assertion", null);
					}
					sendOK(message, res);
					break;
				case "generate-slo-request":
					String sessionIndex = message.body().getString("SessionIndex");
					String nameID = message.body().getString("NameID");
					sendOK(message, new JsonObject().putString("slo", generateSloRequest(nameID, sessionIndex, idp)));
					break;
				default:
					sendError(message, "invalid.action");
			}
		} catch (Exception e) {
			sendError(message, e.getMessage(), e);
		}
	}

	private String generateAuthnRequest(String idp)
			throws NoSuchFieldException, IllegalAccessException, MarshallingException, IOException {
	//	XMLObjectBuilderFactory builderFactory = org.opensaml.Configuration.getBuilderFactory();

		final String id = "ENT_" + getRandomHexString(32); // UUID.randomUUID().toString().replaceAll("\\-","");

		//SAMLObjectBuilder authnRequestBuilder = (SAMLObjectBuilder) builderFactory.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME);
		//AuthnRequest authnRequest = (AuthnRequest) authnRequestBuilder.buildObject();

		//DocumentBuilder builder = factory.newDocumentBuilder();
		//Document authXmlDocument = builder.parse(new InputSource(new StringReader(this.authRequestString)));

		//Create an issuer Object
		Issuer issuer = new IssuerBuilder().buildObject("urn:oasis:names:tc:SAML:2.0:assertion", "Issuer", "samlp" );
		issuer.setValue(this.issuer);

		final NameIDPolicy nameIdPolicy = new NameIDPolicyBuilder().buildObject();
		nameIdPolicy.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:transient");
		//nameIdPolicy.setSPNameQualifier("http://saml20sp.abilityweb.us");
		nameIdPolicy.setAllowCreate(true);

		//Create AuthnContextClassRef
		AuthnContextClassRefBuilder authnContextClassRefBuilder = new AuthnContextClassRefBuilder();
		AuthnContextClassRef authnContextClassRef =
				authnContextClassRefBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion",
						"AuthnContextClassRef", "saml");
		authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
		//Marshaller accrMarshaller = org.opensaml.Configuration.getMarshallerFactory().getMarshaller(authnContextClassRef);
		//org.w3c.dom.Element authnContextClassRefDom = accrMarshaller.marshall(authnContextClassRef);


		RequestedAuthnContext requestedAuthnContext = new RequestedAuthnContextBuilder().buildObject();
		requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.EXACT);
		requestedAuthnContext.getAuthnContextClassRefs().add(authnContextClassRef);
		//requestedAuthnContext.setDOM(authnContextClassRefDom);
		//authnContextClassRef.
		//.setParent((XMLObject) requestedAuthnContext);



		final AuthnRequest authRequest =  new AuthnRequestBuilder()
				.buildObject("urn:oasis:names:tc:SAML:2.0:protocol", "AuthnRequest", "samlp");
		//AuthnRequest request = (AuthnRequest) buildXMLObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
		//authRequest.ASSERTION_CONSUMER_SERVICE_URL_ATTRIB_NAME = "AssertionConsumerServiceURL";
		//authRequest.FORCE_AUTHN_ATTRIB_NAME = "ForceAuthn";
		//authRequest.IS_PASSIVE_ATTRIB_NAME = "IsPassive";
		authRequest.setForceAuthn(false);
		authRequest.setIsPassive(false);
		authRequest.setIssueInstant(new DateTime());
		authRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authRequest.setAssertionConsumerServiceURL("https://preprod-acbordeaux2d.opendigitaleducation.com/auth/saml/acs");
		authRequest.setAssertionConsumerServiceIndex(1);
		authRequest.setAttributeConsumingServiceIndex(1);
		authRequest.setIssuer(issuer);
		authRequest.setNameIDPolicy(nameIdPolicy);
		authRequest.setRequestedAuthnContext(requestedAuthnContext); //TODO: How to connect the AuthnContextClassRef that I created for this object
		authRequest.setID(id);
		authRequest.setVersion(SAMLVersion.VERSION_20);



		final String anr = SamlUtils.marshallAuthnRequest(authRequest);
		logger.debug(anr);
		//final ByteArrayOutputStream bos = deflateContent(anr.getBytes("UTF-8"));

//		// Now we must build our representation to put into the html form to be submitted to the idp
//		Marshaller marshaller = org.opensaml.Configuration.getMarshallerFactory().getMarshaller(authRequest);
//		org.w3c.dom.Element authDOM = marshaller.marshall(authRequest);
//		StringWriter rspWrt = new StringWriter();
//		XMLHelper.writeNode(authDOM, rspWrt);
//		String messageXML = rspWrt.toString();
//		//String samlResponse = new String(Base64.encodeBytes(messageXML.getBytes(), Base64.DONT_BREAK_LINES));
//
//		//delete this area
//		//String temp = "<samlp:AuthnRequest  xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"  ID=\"71069679271a7cf36e0e02e48084798ea844fce23f\" Version=\"2.0\" IssueInstant=\"2010-03-09T10:46:23Z\" ForceAuthn=\"false\" IsPassive=\"false\" ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" AssertionConsumerServiceURL=\"http://saml20sp.abilityweb.us/spdbg/sp.php\"><saml:Issuer xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">http://saml20sp.abilityweb.us</saml:Issuer><samlp:NameIDPolicy  xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:persistent\" SPNameQualifier=\"http://saml20sp.abilityweb.us\" AllowCreate=\"true\"></samlp:NameIDPolicy><samlp:RequestedAuthnContext xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" Comparison=\"exact\"><saml:AuthnContextClassRef xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\">urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport</saml:AuthnContextClassRef></samlp:RequestedAuthnContext></samlp:AuthnRequest>";
		Deflater deflater = new Deflater(Deflater.DEFLATED, true);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(bos, deflater);
		deflaterOutputStream.write(anr.getBytes("UTF-8"));
		deflaterOutputStream.close();
//		String samlResponse = Base64.encodeBytes(byteArrayOutputStream.toByteArray(), Base64.DONT_BREAK_LINES);
//		String outputString = new String(byteArrayOutputStream.toByteArray());
//		//System.out.println("Compressed String: " + outputString);
//		samlResponse = URLEncoder.encode(samlResponse);
//
//		String actionURL = this.redirectionUrl;
//		System.out.println("Converted AuthRequest: " + messageXML);
//		System.out.println("samlResponse: " + samlResponse);
//		//messageXML = messageXML.replace("<", "&lt;");
//		//messageXML = messageXML.replace(">", "&gt;");
//
//		String url = actionURL + "?SAMLRequest=" + samlResponse + "&RelayState=" + this.relayState;
//		System.out.println(url);
		return  getAuthnRequestUri(idp) + "?SAMLRequest=" + URLEncoder.encode(
				Base64.encodeBytes(bos.toByteArray(), Base64.DONT_BREAK_LINES), "UTF-8") +
				"&RelayState=" + UUID.randomUUID().toString();



		//HTTPRedirectDeflateEncoder httpRedirectDeflateEncoder = new HTTPRedirectDeflateEncoder();
		//httpRedirectDeflateEncoder.encode((MessageContext) authDOM);
	}

	private String generateSloRequest(String nameID, String sessionIndex, String idp)
			throws NoSuchFieldException, IllegalAccessException, MarshallingException, IOException {
		NameID nameId = SamlUtils.buildSAMLObjectWithDefaultName(NameID.class);
		nameId.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:entity");
		nameId.setValue(nameID);
		LogoutRequest logoutRequest = SamlUtils.buildSAMLObjectWithDefaultName(LogoutRequest.class);

		logoutRequest.setID(UUID.randomUUID().toString());
		String sloUri = getLogoutUri(idp);
		logoutRequest.setDestination(sloUri);
		logoutRequest.setIssueInstant(new DateTime());

		Issuer issuer = SamlUtils.buildSAMLObjectWithDefaultName(Issuer.class);
		issuer.setValue(this.issuer);
		logoutRequest.setIssuer(issuer);

		SessionIndex sessionIndexElement = SamlUtils.buildSAMLObjectWithDefaultName(SessionIndex.class);

		sessionIndexElement.setSessionIndex(sessionIndex);
		logoutRequest.getSessionIndexes().add(sessionIndexElement);

		logoutRequest.setNameID(nameId);

		byte[] lr = SamlUtils.marshallLogoutRequest(logoutRequest).getBytes("UTF-8");
		ByteArrayOutputStream bos = deflateContent(lr);

		return sloUri + "?SAMLRequest=" + URLEncoder.encode(Base64.encodeBytes(bos.toByteArray()), "UTF-8") +
				"&RelayState=NULL";
	}

	private ByteArrayOutputStream deflateContent(byte[] lr) throws IOException {
		// compress response
		Deflater deflater = new Deflater();
		deflater.setInput(lr);
		deflater.finish();
		ByteArrayOutputStream bos = new ByteArrayOutputStream(lr.length);
		byte[] buffer = new byte[1024];
		while (!deflater.finished()) {
			int bytesCompressed = deflater.deflate(buffer);
			bos.write(buffer,0,bytesCompressed);
		}
		deflater.end();
		bos.close();
		return bos;
	}

	private String getAuthnRequestUri(String idp) {
		String ssoServiceURI = null;
		EntityDescriptor entityDescriptor = entityDescriptorMap.get(idp);
		if (entityDescriptor != null) {
			for (SingleSignOnService ssos : entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS)
					.getSingleSignOnServices()) {
				if (ssos.getBinding().equals(SAMLConstants.SAML2_REDIRECT_BINDING_URI)) {
					ssoServiceURI = ssos.getLocation();
				}
			}
		}
		return ssoServiceURI;
	}

	private String getLogoutUri(String idp) {
		String sloServiceURI = null;
		EntityDescriptor entityDescriptor = entityDescriptorMap.get(idp);
		if (entityDescriptor != null) {
			for (SingleLogoutService sls : entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS)
					.getSingleLogoutServices()) {
				if (sls.getBinding().equals(SAMLConstants.SAML2_REDIRECT_BINDING_URI)) {
					sloServiceURI = sls.getLocation();
				}
			}
		}
		return sloServiceURI;
	}

	public boolean validateSignature(String assertion) throws Exception {
		final Response response = SamlUtils.unmarshallResponse(assertion);
		final SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
		Signature signature = response.getSignature();

		if (signature == null) {
			if (response.getAssertions() != null && !response.getAssertions().isEmpty()) {
				for (Assertion a : response.getAssertions()) {
					signature = a.getSignature();
				}
			} else if (response.getEncryptedAssertions() != null && !response.getEncryptedAssertions().isEmpty()) {
				Assertion a = decryptAssertion(response);
				if (a != null) {
					signature = a.getSignature();
				}
			} else {
				throw new ValidationException("Assertions not founds.");
			}
		}
		if (signature == null) {
			throw new ValidationException("Signature not found.");
		}
		profileValidator.validate(signature);

		SignatureTrustEngine sigTrustEngine =  getSignatureTrustEngine(response);
		CriteriaSet criteriaSet = new CriteriaSet();
		criteriaSet.add(new EntityIDCriteria(SamlUtils.getIssuer(response)));
		criteriaSet.add(new MetadataCriteria(IDPSSODescriptor.DEFAULT_ELEMENT_NAME, SAMLConstants.SAML20P_NS));
		criteriaSet.add(new UsageCriteria(UsageType.SIGNING));

		return sigTrustEngine.validate(signature, criteriaSet);
	}

	private void loadSignatureTrustEngine(String filePath) throws MetadataProviderException {
		logger.info(filePath);
		FilesystemMetadataProvider metadataProvider = new FilesystemMetadataProvider(new File(filePath));
		metadataProvider.setParserPool(new BasicParserPool());
		metadataProvider.initialize();
		MetadataCredentialResolver metadataCredResolver = new MetadataCredentialResolver(metadataProvider);
		KeyInfoCredentialResolver keyInfoCredResolver =
				Configuration.getGlobalSecurityConfiguration().getDefaultKeyInfoCredentialResolver();
		EntityDescriptor entityDescriptor = (EntityDescriptor) metadataProvider.getMetadata();
		String entityID = entityDescriptor.getEntityID();
		entityDescriptorMap.put(entityID, entityDescriptor);
		signatureTrustEngineMap.put(entityID,
				new ExplicitKeySignatureTrustEngine(metadataCredResolver, keyInfoCredResolver));
	}

	private SignatureTrustEngine getSignatureTrustEngine(Response response) {
		return signatureTrustEngineMap.get(SamlUtils.getIssuer(response));
	}

	private String decryptAssertion(String response) throws Exception {
		return SamlUtils.marshallAssertion(decryptAssertion(SamlUtils.unmarshallResponse(response)));
	}

	private Assertion decryptAssertion(Response response) throws Exception {
		EncryptedAssertion encryptedAssertion;
		if (response.getEncryptedAssertions() != null && response.getEncryptedAssertions().size() == 1) {
			encryptedAssertion = response.getEncryptedAssertions().get(0);
		} else {
			throw new ValidationException("Encrypted Assertion not found.");
		}

		BasicX509Credential decryptionCredential = new BasicX509Credential();
		decryptionCredential.setPrivateKey(privateKey);

		Decrypter decrypter = new Decrypter(null, new StaticKeyInfoCredentialResolver(decryptionCredential),
				new InlineEncryptedKeyResolver());
		decrypter.setRootInNewDocument(true);

		Assertion assertion = decrypter.decrypt(encryptedAssertion);

		if (assertion != null && assertion.getSubject() != null && assertion.getSubject().getEncryptedID() != null) {
			SAMLObject s = decrypter.decrypt(assertion.getSubject().getEncryptedID());
			if (s instanceof BaseID) {
				assertion.getSubject().setBaseID((BaseID) s);
			} else if (s instanceof NameID) {
				assertion.getSubject().setNameID((NameID) s);
			}
			assertion.getSubject().setEncryptedID(null);
		}

		if (assertion != null && assertion.getAttributeStatements() != null) {
			for (AttributeStatement statement : assertion.getAttributeStatements()) {
				for (EncryptedAttribute ea : statement.getEncryptedAttributes()) {
					Attribute a = decrypter.decrypt(ea);
					statement.getAttributes().add(a);
				}
				statement.getEncryptedAttributes().clear();
			}
		}
		return assertion;
	}

	private String getRandomHexString(int numchars){
		Random r = new Random();
		StringBuilder sb = new StringBuilder();
		while(sb.length() < numchars){
			sb.append(Integer.toHexString(r.nextInt()));
		}

		return sb.toString().substring(0, numchars);
	}

}
