/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.governance.services.util;

import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.jaxen.JaxenException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.axis2.context.MessageContext;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.extensions.utils.CommonConstants;
import org.wso2.carbon.utils.CarbonUtils;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.namespace.QName;
import java.io.*;
import java.util.Iterator;


import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

public class Util {

    private static final Log log = LogFactory.getLog(Util.class);

    private static RegistryService registryService;

    private static Validator serviceSchemaValidator = null;

    public static synchronized void setRegistryService(RegistryService service) {
        if (registryService == null) {
            registryService = service;
        }
    }

    public static RegistryService getRegistryService() {
        return registryService;
    }

    public static String getServiceConfig(Registry registry)throws Exception{
        return new String((byte[])registry.get(RegistryConstants.GOVERNANCE_SERVICES_CONFIG_PATH + "service").getContent());
    }

//    The methods have been duplicated in several places because there is no common bundle to place them.
//    We have to keep this inside different bundles so that users will not run in to problems if they uninstall some features
    public static boolean validateOMContent(OMElement omContent, Validator validator) {
        try {
            InputStream is = new ByteArrayInputStream(omContent.toString().getBytes("utf-8"));
            Source xmlFile = new StreamSource(is);
            if (validator != null) {
                validator.validate(xmlFile);
            }
        } catch (SAXException e) {
            log.error("Unable to validate the given xml configuration ",e);
            return false;
        } catch (UnsupportedEncodingException e) {
            log.error("Unsupported content");
            return false;
        } catch (IOException e) {
            log.error("Unable to validate the given file");
            return false;
        }
        return true;
    }

    public static Validator getSchemaValidator(String schemaPath){

        if (serviceSchemaValidator == null) {
            try {
                SchemaFactory schemaFactory = SchemaFactory
                        .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = schemaFactory.newSchema(new File(schemaPath));
                serviceSchemaValidator = schema.newValidator();
            } catch (SAXException e) {
                log.error("Unable to get a schema validator from the given file path : " + schemaPath);
            }
        }
        return serviceSchemaValidator;
    }

    public static String getServicesSchemaLocation(){
        return CarbonUtils.getCarbonHome() + File.separator + "repository" + File.separator +
                "conf" + File.separator + "service-ui-config.xsd";
    }

    public static void validateOMContent(OMElement element) throws RegistryException {
        if(!validateOMContent(element,getSchemaValidator(getServicesSchemaLocation()))){
            String message = "Unable to validate the xml configuration";
            log.error(message);
            throw new RegistryException(message);
        }
    }

    public static OMElement buildOMElement(String payload) throws RegistryException {
        OMElement element;
        try {
            element = AXIOMUtil.stringToOM(payload);
            element.build();
        } catch (Exception e) {
            String message = "Unable to parse the XML configuration. Please validate the XML configuration";
            log.error(message,e);
            throw new RegistryException(message,e);
        }
        return element;
    }
}
