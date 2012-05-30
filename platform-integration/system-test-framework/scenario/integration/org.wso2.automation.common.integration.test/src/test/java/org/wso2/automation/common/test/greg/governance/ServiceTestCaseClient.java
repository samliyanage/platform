/*
* Copyright (c) 2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.automation.common.test.greg.governance;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.governance.api.common.dataobjects.GovernanceArtifact;
import org.wso2.carbon.governance.api.endpoints.EndpointManager;
import org.wso2.carbon.governance.api.endpoints.dataobjects.Endpoint;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.policies.PolicyManager;
import org.wso2.carbon.governance.api.policies.dataobjects.Policy;
import org.wso2.carbon.governance.api.schema.dataobjects.Schema;
import org.wso2.carbon.governance.api.services.ServiceFilter;
import org.wso2.carbon.governance.api.services.ServiceManager;
import org.wso2.carbon.governance.api.services.dataobjects.Service;
import org.wso2.carbon.governance.api.util.GovernanceArtifactConfiguration;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.governance.api.wsdls.WsdlManager;
import org.wso2.carbon.governance.api.wsdls.dataobjects.Wsdl;
import org.wso2.carbon.registry.api.Association;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;
import org.wso2.platform.test.core.ProductConstant;
import org.wso2.platform.test.core.utils.gregutils.GregUserIDEvaluator;
import org.wso2.platform.test.core.utils.gregutils.RegistryProvider;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileInputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertTrue;

public class ServiceTestCaseClient {
    private static final Log log = LogFactory.getLog(ServiceTestCaseClient.class);
    private Registry governance;
    private String configPath;
    private WSRegistryServiceClient registryWS;

    @BeforeClass(groups = {"wso2.greg"})
    public void initTest() throws RegistryException, AxisFault {
        int userId = new GregUserIDEvaluator().getTenantID();
        registryWS = new RegistryProvider().getRegistry(userId, ProductConstant.GREG_SERVER_NAME);
        governance = new RegistryProvider().getGovernance(registryWS, userId);

        configPath = ProductConstant.SYSTEM_TEST_RESOURCE_LOCATION + File.separator +
                     "artifacts" + File.separator + "GREG" + File.separator + "services" +
                     File.separator + "service.metadata.xml";

    }

    @Test(groups = {"wso2.greg"})
    public void testAddService() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);

        Service service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep", "MyService"));
        service.addAttribute("testAttribute", "somevalue");
        serviceManager.addService(service);

        String serviceId = service.getId();
        Service newService = serviceManager.getService(serviceId);

        Assert.assertEquals(newService.getAttribute("testAttribute"), "somevalue");

        service.addAttribute("testAttribute", "somevalue2");
        serviceManager.updateService(service);

        newService = serviceManager.getService(serviceId);

        String[] values = newService.getAttributes("testAttribute");

        Assert.assertEquals(values.length, 2);
    }

    @Test(groups = {"wso2.greg"})
    public void testServiceSearch() throws Exception {
        File file = new File(configPath);
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileContents = new byte[(int) file.length()];
        fileInputStream.read(fileContents);

        OMElement contentElement = GovernanceUtils.buildOMElement(fileContents);

        ServiceManager serviceManager = new ServiceManager(governance);
        Service service = serviceManager.newService(contentElement);

        service.addAttribute("custom-attribute", "custom-value");
        serviceManager.addService(service);


        // so retrieve it back
        String serviceId = service.getId();
        Service newService = serviceManager.getService(serviceId);

        Assert.assertEquals(newService.getAttribute("custom-attribute"), "custom-value");
        Assert.assertEquals(newService.getAttribute("interface_wsdlURL"),
                            "/_system/governance/trunk/wsdls/com/foo/abc.wsdl");
        Assert.assertEquals(newService.getQName(), service.getQName());

        Service service2 = serviceManager.newService(new QName("http://baps.paps.mug/done", "meep"));
        service2.addAttribute("custom-attribute", "custom-value2");
        serviceManager.addService(service2);

        Service service3 = serviceManager.newService(new QName("http://baps.paps.mug/jug", "peem"));
        service3.addAttribute("custom-attribute", "not-custom-value");
        serviceManager.addService(service3);

        Service service4 = serviceManager.newService(new QName("http://baps.dadan.mug/jug", "doon"));
        service4.addAttribute("not-custom-attribute", "custom-value3");
        serviceManager.addService(service4);

        Service[] services = serviceManager.findServices(new ServiceFilter() {
            public boolean matches(Service service) throws GovernanceException {
                String attributeVal = service.getAttribute("custom-attribute");
                return attributeVal != null && attributeVal.startsWith("custom-value");
            }
        });

//        Assert.assertEquals(services.length, 2);
        Assert.assertTrue(services[0].getQName().equals(service.getQName()) ||
                          services[0].getQName().equals(service2.getQName()));
        Assert.assertTrue(services[1].getQName().equals(service.getQName()) ||
                          services[1].getQName().equals(service2.getQName()));

        // update the service2
        service2.setQName(new QName("http://bom.bom.com/baaaang", "bingo"));
        serviceManager.updateService(service2);

//        newService = serviceManager.getService(service2.getId());
        Assert.assertEquals(service2.getAttribute("custom-attribute"), "custom-value2");


        // do the test again
        services = serviceManager.findServices(new ServiceFilter() {
            public boolean matches(Service service) throws GovernanceException {
                String attributeVal = service.getAttribute("custom-attribute");
                return attributeVal != null && attributeVal.startsWith("custom-value");
            }
        });

        Assert.assertEquals(services.length, 2);
        Assert.assertTrue(services[0].getQName().equals(service.getQName()) ||
                          services[0].getQName().equals(service2.getQName()));
        Assert.assertTrue(services[1].getQName().equals(service.getQName()) ||
                          services[1].getQName().equals(service2.getQName()));
    }

    @Test(groups = {"wso2.greg"})
    public void testServiceAttachments() throws Exception {
        // first put a WSDL
        WsdlManager wsdlManager = new WsdlManager(governance);

        Wsdl wsdl = wsdlManager.newWsdl("https://svn.wso2.org/repos/wso2/carbon/platform/trunk/" +
                                        "platform-integration/system-test-framework/core/" +
                                        "org.wso2.automation.platform.core/src/main/resources/" +
                                        "artifacts/GREG/wsdl/BizService.wsdl");
        wsdlManager.addWsdl(wsdl);

        ServiceManager serviceManager = new ServiceManager(governance);
        Service service = serviceManager.newService(new QName("http://test/org/bang", "myservice"));
        serviceManager.addService(service);

        service.attachWSDL(wsdl);

        Wsdl[] wsdls = service.getAttachedWsdls();

        Assert.assertEquals(wsdls.length, 1);
        Assert.assertEquals(wsdls[0].getQName(), new QName("http://foo.com", "BizService.wsdl"));

        Schema[] schemas = wsdls[0].getAttachedSchemas();

        Assert.assertEquals(schemas.length, 1);
        Assert.assertEquals(schemas[0].getQName(),
                            new QName("http://bar.org/purchasing", "purchasing.xsd"));
        Assert.assertNotNull(schemas[0].getId());

        // now add a policy.
        PolicyManager policyManager = new PolicyManager(governance);

        Policy policy = policyManager.newPolicy(
                                                       "http://svn.wso2.org/repos/wso2/tags/wsf/php/2.1.0/samples/security/" +
                                                       "complete/policy.xml");
        policy.setName("mypolicy.xml");
        policyManager.addPolicy(policy);

        service.attachPolicy(policy);

        Policy[] policies = service.getAttachedPolicies();
        Assert.assertEquals(policies.length, 1);
        Assert.assertEquals(policies[0].getQName(), new QName("mypolicy.xml"));

        File file = new File(configPath);
//        FileInputStream fileInputStream = new FileInputStream(file);
//        byte[] fileContents = new byte[(int) file.length()];
//        fileInputStream.read(fileContents);

//        OMElement contentElement = GovernanceUtils.buildOMElement(fileContents);
//
        // service = serviceManager.newService(contentElement);
        //serviceManager.addService(service);

        String[] serviceIds = serviceManager.getAllServiceIds();
        for (String serviceId : serviceIds) {
            Service servicex = serviceManager.getService(serviceId);
            Policy[] policiesx = servicex.getAttachedPolicies();
            if (policiesx != null && policiesx.length != 0) {
                Assert.assertEquals(policiesx[0].getQName(), new QName("mypolicy.xml"));
            }
        }

    }

    @Test(groups = {"wso2.greg"})
    public void testServiceRename() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);

        Service service = serviceManager.newService(new QName("http://banga.queek.queek/blaa", "sfosf"));
        serviceManager.addService(service);

        service.setQName(new QName("http://doc.x.ge/yong", "almdo"));
        serviceManager.updateService(service);

        Service exactServiceCopy = serviceManager.getService(service.getId());
        QName qname = exactServiceCopy.getQName();

        Assert.assertEquals(new QName("http://doc.x.ge/yong", "almdo"), qname);
        Assert.assertEquals(exactServiceCopy.getPath(), "/trunk/services/ge/x/doc/yong/almdo");


        // doing the same for a meta data service
        File file = new File(configPath);
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileContents = new byte[(int) file.length()];
        fileInputStream.read(fileContents);

        OMElement contentElement = GovernanceUtils.buildOMElement(fileContents);

        service = serviceManager.newService(contentElement);
        serviceManager.addService(service);

        service.setQName(new QName("http://doc.x.ge/yong", "almdo"));
        serviceManager.updateService(service);

        exactServiceCopy = serviceManager.getService(service.getId());
        qname = exactServiceCopy.getQName();

        Assert.assertEquals(qname, new QName("http://doc.x.ge/yong", "almdo"));
        Assert.assertEquals(exactServiceCopy.getPath(), "/trunk/services/ge/x/doc/yong/almdo");

    }

    @Test(groups = {"wso2.greg"})
    public void testServiceDelete() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);

        Service service = serviceManager.newService(new QName("http://banga.doom.queek/blaa", "lmnop"));
        serviceManager.addService(service);

        Service newService = serviceManager.getService(service.getId());
        Assert.assertNotNull(newService);

        serviceManager.removeService(newService.getId());
        newService = serviceManager.getService(service.getId());
        Assert.assertNull(newService);


        service = serviceManager.newService(new QName("http://banga.bang.queek/blaa", "basss"));
        serviceManager.addService(service);

        newService = serviceManager.getService(service.getId());
        Assert.assertNotNull(newService);

        governance.delete(newService.getPath());
        newService = serviceManager.getService(service.getId());
        Assert.assertNull(newService);
    }

    @Test(groups = {"wso2.greg"}, description = "add a duplicate service again with different " +
                                                "namespace")
    public void testDuplicateService() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);

        Service service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep",
                                                              "DuplicateService2"));
        service.addAttribute("testAttribute", "duplicate1");
        serviceManager.addService(service);
        String serviceId = service.getId();


        Service newService = serviceManager.getService(serviceId);
        assertTrue(newService.getQName().toString().contains("DuplicateService2"));
        assertEquals(newService.getAttribute("testAttribute"), "duplicate1");

        //try to add same service again
        ServiceManager serviceManagerDuplicate = new ServiceManager(governance);
        Service duplicateService = serviceManagerDuplicate.newService(new QName("http://bang.boom" +
                                                                                ".com/mnm/beep2",
                                                                                "DuplicateService2"));
        duplicateService.addAttribute("testAttributeDuplicate", "duplicate2");
        serviceManagerDuplicate.addService(duplicateService);

        Service newServiceDuplicate = serviceManagerDuplicate.getService(duplicateService.getId());
        assertTrue(newServiceDuplicate.getQName().toString().contains("DuplicateService2"));
        assertEquals(newServiceDuplicate.getAttribute("testAttributeDuplicate"), "duplicate2");

        governance.delete(newService.getPath());
        governance.delete(duplicateService.getPath());
        newService = serviceManager.getService(service.getId());
        newServiceDuplicate = serviceManagerDuplicate.getService(duplicateService.getId());
        Assert.assertNull(newService);
        Assert.assertNull(newServiceDuplicate);
    }

    @Test(groups = {"wso2.greg"}, description = "add a duplicate service again with same " +
                                                "namespace")
    public void testDuplicateServiceWithNameSpaces() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);

        Service service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep",
                                                              "Service1"));
        service.addAttribute("testAttribute", "service1");
        serviceManager.addService(service);
        String serviceId = service.getId();

        Service newService = serviceManager.getService(serviceId);
        assertTrue(newService.getQName().toString().contains("Service1"));
        assertEquals(newService.getAttribute("testAttribute"), "service1");

        //try to add same service again
        ServiceManager serviceManagerDuplicate = new ServiceManager(governance);
        Service duplicateService = serviceManagerDuplicate.newService(new QName("http://bang.boom.com/mnm/beep",
                                                                                "Service1"));
        duplicateService.addAttribute("testAttributeDuplicate", "duplicate2");
        serviceManagerDuplicate.addService(duplicateService);

        Service newServiceDuplicate = serviceManagerDuplicate.getService(duplicateService.getId());
        assertTrue(newServiceDuplicate.getQName().toString().contains("Service1"));
        assertEquals(newServiceDuplicate.getAttribute("testAttributeDuplicate"), "duplicate2");

        //add duplicate service though same service manger.

        service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep",
                                                      "Service1"));
        service.addAttribute("testAttribute", "service1");
        serviceManager.addService(service);

        newService = serviceManager.getService(service.getId());
        assertTrue(newService.getQName().toString().contains("Service1"));
        assertEquals(newService.getAttribute("testAttribute"), "service1");

        governance.delete(newService.getPath());
        newService = serviceManager.getService(service.getId());
        newServiceDuplicate = serviceManagerDuplicate.getService(duplicateService.getId());
        Assert.assertNull(newService);
        Assert.assertNull(newServiceDuplicate);
    }

    @Test(groups = {"wso2.greg"}, description = "add service with special characters", dataProvider = "invalidCharacter")
    public void testServiceWithSpecialCharacters(String invalidCharacters) throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);
        boolean status = false;
        Service service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep",
                                                              "Service1" + invalidCharacters));
        service.addAttribute("testAttribute", "service1");
        try {
            serviceManager.addService(service);
        } catch (GovernanceException ignored) {
            status = true;
            log.info("Adding service with invalid Character " + invalidCharacters);
        }
        assertTrue("Invalid service added with special character - " + invalidCharacters, status);
    }

    @Test(groups = {"wso2.greg"}, description = "add 10000 resources to registry")
    public void testAddLargeNumberOfServices() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);
        Service newService;
        final String serviceName = "WSO2AutomatedService";
        int numberOfServices = 10 * 1000; //10000 services
        for (int i = 1; i <= numberOfServices; i++) {
            Service service = serviceManager.newService(new QName("http://wso2.test" +
                                                                  ".automation/boom/test" + i,
                                                                  serviceName + i));
            service.addAttribute("testAttribute", "service" + i);
            serviceManager.addService(service);
            String serviceId = service.getId();
            newService = serviceManager.getService(serviceId);
            assertTrue(newService.getQName().toString().contains(serviceName + i));
            assertEquals(newService.getAttribute("testAttribute"), "service" + i);

        }
        assertTrue("Less than " + numberOfServices + " services exists",
                   serviceManager.getAllServices().length >= numberOfServices);

        assertTrue("Less than " + numberOfServices + "  ids exists",
                   serviceManager.getAllServiceIds().length >= numberOfServices);

        String[] servicePaths = serviceManager.getAllServicePaths();

        assertTrue("Less than " + numberOfServices + "  paths exists",
                   serviceManager.getAllServicePaths().length >= numberOfServices);

        //delete services
        for (String servicePath : servicePaths) {
            if (servicePath.contains(serviceName)) {
                governance.delete(servicePath);
                numberOfServices--;
                ServiceFilter filter = new ServiceFilter() {
                    public boolean matches(Service service) throws GovernanceException {
                        return service.getQName().toString().contains(serviceName);
                    }
                };
                assertTrue(serviceManager.findServices(filter).length == numberOfServices);
            }
        }
    }

    @Test(groups = {"wso2.greg"}, description = "Service activate and deactivate")
    public void testServiceStatus() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);

        Service service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep",
                                                              "WSO2AutomationActiveService"));
        service.addAttribute("testAttribute", "serviceAttr");
        serviceManager.addService(service);
        String serviceId = service.getId();
        assertTrue(service.isActive());
        service.activate();
        assertTrue(service.isActive());
        service.deactivate();
        assertFalse(service.isActive());

        Service newService = serviceManager.getService(serviceId);
        assertTrue(newService.getQName().toString().contains("WSO2AutomationActiveService"));
        assertEquals(newService.getAttribute("testAttribute"), "serviceAttr");

        serviceManager.removeService(service.getId());
        assertNull(serviceManager.getService(service.getId()));
    }

    @Test(groups = {"wso2.greg"}, description = "Attache 100 endpoints to a service")
    public void testAttachLargeNumberOfEndpoints() throws RegistryException {
        String service_namespace = "http://wso2.org/atomation/test";
        String service_name = "ServiceForLargeNumberOfEndpoints";
        int numberOfEndPoints = 100;

        ServiceManager serviceManager = new ServiceManager(governance);
        Service service;
        service = serviceManager.newService(new QName(service_namespace, service_name));
        serviceManager.addService(service);
        EndpointManager endpointManager = new EndpointManager(governance);

        for (int i = 1; i <= numberOfEndPoints; i++) {
            Endpoint ep1 = endpointManager.newEndpoint("http://wso2.automation" +
                                                       ".endpoint" + i);
            endpointManager.addEndpoint(ep1);
            service.attachEndpoint(ep1);
        }

        Endpoint[] endpoints = service.getAttachedEndpoints();
        assertEquals(numberOfEndPoints, endpoints.length);

        //Detach Endpoint one
        for (Endpoint endpoint : endpoints) {
            service.detachEndpoint(endpoint.getId());
            numberOfEndPoints--;
            Assert.assertTrue(numberOfEndPoints == service.getAttachedEndpoints().length);
        }

        //remove the service
        serviceManager.removeService(service.getId());
        assertNull(serviceManager.getService(service.getId()));
    }

    @Test(groups = {"wso2.greg"}, description = "Attache 100 policies to a service")
    public void testAttachLargeNumberOfPolicies() throws RegistryException {
        String service_namespace = "http://wso2.org/atomation/test";
        String service_name = "ServiceForLargeNumberOfPolicies1";
        int numberOfPolicies = 10;

        ServiceManager serviceManager = new ServiceManager(governance);
        Service service;
        service = serviceManager.newService(new QName(service_namespace, service_name));
        serviceManager.addService(service);
        PolicyManager policyManager = new PolicyManager(governance);

        for (int i = 1; i <= numberOfPolicies; i++) {
            Policy policy = policyManager.newPolicy("https://svn.wso2.org/repos/wso2/carbon/platform" +
                                                    "/trunk/platform-integration/system-test-framework" +
                                                    "/core/org.wso2.automation.platform.core/src/main" +
                                                    "/resources/artifacts/GREG/policy/UTPolicy.xml");
            policy.setName("testPolicy" + i);
            policyManager.addPolicy(policy);
            service.attachPolicy(policy);
        }

        Policy[] policies = service.getAttachedPolicies();
        assertEquals(numberOfPolicies, policies.length);

        //Detach Endpoint one
        for (Policy policy : policies) {
            service.detachPolicy(policy.getId());
            numberOfPolicies--;
            Assert.assertTrue(numberOfPolicies == service.getAttachedPolicies().length);
        }

        //remove the service
        serviceManager.removeService(service.getId());
        assertNull(serviceManager.getService(service.getId()));
    }

    @Test(groups = {"wso2.greg"}, description = "Add a wsdl to servcie with schema imports")
    public void testAttacheWsdlWithSchemaImports() throws Exception {
        // first put a WSDL
        WsdlManager wsdlManager = new WsdlManager(governance);

        Wsdl wsdl = wsdlManager.newWsdl("https://svn.wso2.org/repos/wso2/carbon/platform/trunk/" +
                                        "platform-integration/system-test-framework/core/" +
                                        "org.wso2.automation.platform.core/src/main/resources/" +
                                        "artifacts/GREG/wsdl/BizService.wsdl");
        wsdlManager.addWsdl(wsdl);

        ServiceManager serviceManager = new ServiceManager(governance);
        Service service = serviceManager.newService(new QName("http://test/org/bang", "BizService"));
        serviceManager.addService(service);

        service.attachWSDL(wsdl);
        Wsdl[] wsdls = service.getAttachedWsdls();

        Assert.assertEquals(wsdls.length, 1);
        Assert.assertEquals(wsdls[0].getQName(), new QName("http://foo.com", "BizService.wsdl"));
        Schema[] schemas = wsdls[0].getAttachedSchemas();

        Assert.assertEquals(schemas.length, 1);
        Assert.assertEquals(schemas[0].getQName(),
                            new QName("http://bar.org/purchasing", "purchasing.xsd"));
        Assert.assertNotNull(schemas[0].getId());

        Association[] associations = governance.getAllAssociations(service.getPath());

        verifyServiceAssociations(associations);

        service.detachWSDL(wsdls[0].getId());
        assertTrue("WSDL still exits ", service.getAttachedWsdls().length == 0);

        service.detachSchema(schemas[0].getId());
        assertTrue("Schema still exits ", service.getAttachedSchemas().length == 0);

        //remove the service
        serviceManager.removeService(service.getId());
        assertNull(serviceManager.getService(service.getId()));
    }

    private boolean verifyServiceAssociations(Association[] associations) {
        boolean status = false;
        for (Association association : associations) {
            if (association.getAssociationType().equals("depends") &&
                association.getDestinationPath().contains("ep-")) {
                assertTrue(association.getAssociationType().equals("depends"));
                assertTrue(association.getSourcePath().contains("/test/org/bang/BizService"));
                assertTrue(association.getDestinationPath().contains("endpoints/localhost/axis2/services/ep-BizService"));
                status = true;

            } else if (association.getAssociationType().equals("depends") &&
                       association.getDestinationPath().contains(".wsdl")) {
                assertTrue(association.getAssociationType().equals("depends"));
                assertTrue(association.getSourcePath().contains("/test/org/bang/BizService"));
                assertTrue(association.getDestinationPath().contains("/wsdls/com/foo/BizService" +
                                                                     ".wsdl"));
                status = true;


            } else if (association.getAssociationType().equals("usedBy")
                       && association.getSourcePath().contains("ep-")) {
                assertTrue(association.getAssociationType().equals("usedBy"));
                assertTrue(association.getSourcePath().contains("endpoints/localhost/axis2/services/ep-BizService"));
                assertTrue(association.getDestinationPath().contains("services/test/org/bang/BizService"));
                status = true;

            } else if (association.getAssociationType().equals("usedBy")
                       && association.getSourcePath().contains(".wsdl")) {
                assertTrue(association.getAssociationType().equals("usedBy"));
                assertTrue(association.getSourcePath().contains("/wsdls/com/foo/BizService" +
                                                                ".wsdl"));
                assertTrue(association.getDestinationPath().contains("services/test/org/bang/BizService"));
                status = true;

            } else if (!association.getAssociationType().equals("usedBy") ||
                       association.getAssociationType().equals("depends")) {
                fail("Required association types usedBy or depends not found");
                status = false;

            }
        }
        return status;
    }

    @Test(groups = {"wso2.greg"}, description = "Attach LC to a service")
    public void testAttachLifeCycleToService() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);

        Service service = serviceManager.newService(new QName("http://bang.boom.com/mnm/beep",
                                                              "LCService"));
        service.addAttribute("testAttribute", "serviceAttr");
        serviceManager.addService(service);
        String serviceId = service.getId();
        service.attachLifecycle("ServiceLifeCycle");
        assertEquals(service.getLifecycleName(), "ServiceLifeCycle");
        assertEquals(service.getLifecycleState(), "Development");

        Service newService = serviceManager.getService(serviceId);
        assertTrue(newService.getQName().toString().contains("WSO2AutomationActiveService"));
        assertEquals(newService.getAttribute("testAttribute"), "serviceAttr");

        serviceManager.removeService(service.getId());
        assertNull(serviceManager.getService(service.getId()));
    }

    @Test(groups = {"wso2.greg"}, description = "Attach Not existing LC to a service")
    public void testAttachNonExistingLCToService() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);
        boolean status = false;
        Service service = serviceManager.newService(new QName("http://wso2.org/automation/test",
                                                              "NonLCService"));
        service.addAttribute("testAttribute", "serviceAttr");
        serviceManager.addService(service);
        String serviceId = service.getId();
        try {
            service.attachLifecycle("ServiceLifeCycleNonExisting");
        } catch (GovernanceException ignored) {
            status = true;
            log.info("Cannot add invalid LC to service");
        }
        assertTrue("LC not get added", status);
        assertNull(service.getLifecycleName());
        assertNull(service.getLifecycleState());

        Service newService = serviceManager.getService(serviceId);
        assertTrue(newService.getQName().toString().contains("NonLCService"));
        assertEquals(newService.getAttribute("testAttribute"), "serviceAttr");

        serviceManager.removeService(service.getId());
        assertNull(serviceManager.getService(service.getId()));
    }


    @Test(groups = {"wso2.greg"}, description = "Attach Not existing LC to a service")
    public void testCreateServiceVersions() throws Exception {
        ServiceManager serviceManager = new ServiceManager(governance);
        Service service = serviceManager.newService(new QName("http://wso2.org/automation/test",
                                                              "ServiceVersions1"));
        service.addAttribute("testAttribute", "serviceAttr");
        serviceManager.addService(service);
        String serviceId = service.getId();
        int versionCountBefore = governance.getVersions(service.getPath()).length;

        //create service versions
        service.createVersion();
        service.createVersion();

        int versionCountAfter = governance.getVersions(service.getPath()).length;
        assertTrue((versionCountAfter - versionCountBefore) == 2);

        //get last version and asset for service name
        assertTrue(governance.getVersions(service.getPath())[versionCountAfter - 1].contains("ServiceVersions1;version"));

        Service newService = serviceManager.getService(serviceId);
        assertTrue(newService.getQName().toString().contains("ServiceVersions1"));
        assertEquals(newService.getAttribute("testAttribute"), "serviceAttr");

        serviceManager.removeService(service.getId());
        assertNull(serviceManager.getService(service.getId()));
    }


    @DataProvider(name = "invalidCharacter")
    public Object[][] invalidCharacter() {
        return new Object[][]{
                                     {"<a>"},
                                     {"#"},
                                     {"a|b"},
                                     {"@"},
                                     {"|"},
                                     {"^"},
                                     {"abc^"},
                                     {"\\"},
                                     {"{"},
                                     {"}"},
                                     {"%"},
                                     {"+"},
                                     {"="},
                                     {"}"},
                                     {"*"},
                                     {";"},
        };
    }


}
