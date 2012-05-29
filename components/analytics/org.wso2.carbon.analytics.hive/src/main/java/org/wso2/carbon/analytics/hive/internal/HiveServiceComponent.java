/**
 * Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.analytics.hive.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.common.ServerUtils;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.service.HiveServer;
import org.apache.synapse.commons.datasource.DataSourceInformation;
import org.apache.synapse.commons.datasource.DataSourceInformationRepository;
import org.apache.synapse.commons.datasource.factory.DataSourceFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.analytics.hive.HiveConstants;
import org.wso2.carbon.analytics.hive.ServiceHolder;
import org.wso2.carbon.analytics.hive.conf.HiveConnectionManager;
import org.wso2.carbon.analytics.hive.impl.HiveExecutorServiceImpl;
import org.wso2.carbon.analytics.hive.service.HiveExecutorService;
import org.wso2.carbon.datasource.DataSourceInformationRepositoryService;
import org.wso2.carbon.ntask.common.TaskException;
import org.wso2.carbon.ntask.core.TaskManager;
import org.wso2.carbon.ntask.core.service.TaskService;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ConfigurationContextService;
import org.wso2.securevault.secret.SecretInformation;

import javax.sql.DataSource;
import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @scr.component name="bam.hive.component" immediate="true"
 * @scr.reference name="registry.service" interface="org.wso2.carbon.registry.core.service.RegistryService"
 * cardinality="1..1" policy="dynamic" bind="setRegistryService" unbind="unsetRegistryService"
 * @scr.reference name="config.context.service"
 * interface="org.wso2.carbon.utils.ConfigurationContextService" cardinality="0..1" policy="dynamic"
 * bind="setConfigurationContextService" unbind="unsetConfigurationContextService"
 * @scr.reference name="ntask.component" interface="org.wso2.carbon.ntask.core.service.TaskService"
 * cardinality="1..1" policy="dynamic" bind="setTaskService" unbind="unsetTaskService"
 * @scr.reference name="datasource.component" interface="org.wso2.carbon.datasource.DataSourceInformationRepositoryService"
 * cardinality="1..1" policy="dynamic" bind="setDataSourceInformationRepositoryService" unbind="unsetDataSourceInformationRepositoryService"
 */

public class HiveServiceComponent {

    private static final Log log = LogFactory.getLog(HiveServiceComponent.class);

    private static final String CARBON_HOME_ENV = "CARBON_HOME";

    private static final String LOG4J_LOCATION = "repository" + File.separator + "conf" +
                                                 File.separator + "log4j.properties";

    private static final String LOG4J_PROPERTY = "log4j.properties";

    private ServiceRegistration hiveServiceRegistration;

    private ExecutorService hiveServerPool = Executors.newSingleThreadExecutor();

    protected void activate(ComponentContext ctx) {

        if (log.isDebugEnabled()) {
            log.debug("Starting 'HiveServiceComponent'");
        }

        // Set CARBON_HOME if not already set for the use of Hive in order to load hive configurations.
        String carbonHome = System.getProperty(CARBON_HOME_ENV);
        if (carbonHome == null) {
            carbonHome = CarbonUtils.getCarbonHome();
            System.setProperty(CARBON_HOME_ENV, carbonHome);
        }

        // Setting up log4j system property so that forked VM during local mode execution can obtain
        // carbon log4j configurations

        String log4jFile = carbonHome + File.separator + LOG4J_LOCATION;
        System.setProperty(LOG4J_PROPERTY, log4jFile);

        hiveServerPool.submit(new HiveRunnable());

        // Set and register HiveExecutorService
        ServiceHolder.setHiveExecutorService(new HiveExecutorServiceImpl());

        hiveServiceRegistration = ctx.getBundleContext().registerService(
                HiveExecutorService.class.getName(),
                ServiceHolder.getHiveExecutorService(),
                null);
/*        HiveConnectionManager connectionManager = HiveConnectionManager.getInstance();
        connectionManager.loadHiveConnectionConfiguration(ctx.getBundleContext());*/

        TaskService taskService = ServiceHolder.getTaskService();

        try {
            taskService.registerTaskType(HiveConstants.HIVE_TASK);
            TaskManager taskManager =
                    taskService.getTaskManager(HiveConstants.HIVE_TASK);
            ServiceHolder.setTaskManager(taskManager);
        } catch (TaskException e) {
            log.error("Error while initializing TaskManager. Script scheduling may not" +
                      " work properly..", e);
        }

        DataSourceInformationRepositoryService dataSourceInfoService = ServiceHolder.
                getDataSourceInformationRepositoryService();
        DataSourceInformationRepository repository = dataSourceInfoService.
                getDataSourceInformationRepository();

        // Registers HIVE DataSource used to connect to Hive service at component startup if not
        // already existing
        DataSourceInformation info = repository.
                getDataSourceInformation(HiveConstants.DEFAULT_HIVE_DATASOURCE);
        if (info == null) {
            info = new DataSourceInformation();
            info.setDatasourceName(HiveConstants.DEFAULT_HIVE_DATASOURCE);
            info.setDriver(HiveConstants.HIVE_DRIVER);
            info.setUrl(HiveConstants.HIVE_DEFAULT_URL);

            SecretInformation secretInformation = new SecretInformation();
            secretInformation.setUser(HiveConstants.HIVE_DEFAULT_USER);

            info.setSecretInformation(secretInformation);

            secretInformation = new SecretInformation();
            secretInformation.setAliasSecret(HiveConstants.HIVE_DEFAULT_PASSWORD);

            info.setSecretInformation(secretInformation);

            repository.addDataSourceInformation(info);

        }

        DataSource dataSource = DataSourceFactory.createDataSource(info);
        if (dataSource == null) {
            log.error("Hive DataSource cannot be created..");
        }

        // Initialize HiveConnectionManager
        HiveConnectionManager connectionManager = HiveConnectionManager.getInstance();
        connectionManager.initialize(dataSource);

        ServiceHolder.setHiveConnectionManager(connectionManager);

    }

    protected void deactivate(ComponentContext ctxt) {

        if (log.isDebugEnabled()) {
            log.debug("Stopping 'HiveServiceComponent'");
        }
        ctxt.getBundleContext().ungetService(hiveServiceRegistration.getReference());

    }

    protected void setRegistryService(RegistryService registryService) throws RegistryException {
        ServiceHolder.setRegistryService(registryService);
    }

    protected void unsetRegistryService(RegistryService registryService) {
        ServiceHolder.setRegistryService(null);
    }

    protected void setTaskService(TaskService taskService) throws RegistryException {
        ServiceHolder.setTaskService(taskService);
    }

    protected void unsetTaskService(TaskService taskService) {
        ServiceHolder.setTaskService(null);
    }

    protected void setConfigurationContextService(ConfigurationContextService contextService) {
        ServiceHolder.setConfigurationContextService(contextService);
    }

    protected void unsetConfigurationContextService(ConfigurationContextService contextService) {
        ServiceHolder.setConfigurationContextService(null);
    }

    protected void setDataSourceInformationRepositoryService(
            DataSourceInformationRepositoryService dataSourceInfoService) {
        ServiceHolder.setDataSourceInformationRepositoryService(dataSourceInfoService);

    }

    protected void unsetDataSourceInformationRepositoryService(
            DataSourceInformationRepositoryService dataSourceInfoService) {
        ServiceHolder.setDataSourceInformationRepositoryService(null);
    }

    public class HiveRunnable implements Runnable {

        public void run() {
            initialize();
        }

        public void initialize() {
            try {
                HiveServer.HiveServerCli cli = new HiveServer.HiveServerCli();

                cli.parse(null);

                // NOTE: It is critical to do this prior to initializing log4j, otherwise
                // any log specific settings via hiveconf will be ignored
                Properties hiveconf = cli.addHiveconfToSystemProperties();

                // NOTE: It is critical to do this here so that log4j is reinitialized
                // before any of the other core hive classes are loaded
/*                try {
                    LogUtils.initHiveLog4j();
                } catch (LogUtils.LogInitializationException e) {
                    HiveServer.HiveServerHandler.LOG.warn(e.getMessage());
                }*/

                HiveConf conf = new HiveConf(HiveServer.HiveServerHandler.class);
                ServerUtils.cleanUpScratchDir(conf);
                TServerTransport serverTransport = new TServerSocket(cli.port);

                // set all properties specified on the command line
                for (Map.Entry<Object, Object> item : hiveconf.entrySet()) {
                    conf.set((String) item.getKey(), (String) item.getValue());
                }

                HiveServer.ThriftHiveProcessorFactory hfactory =
                        new HiveServer.ThriftHiveProcessorFactory(null, conf);
                TThreadPoolServer.Args sargs = new TThreadPoolServer.Args(serverTransport)
                        .processorFactory(hfactory)
                        .transportFactory(new TTransportFactory())
                        .protocolFactory(new TBinaryProtocol.Factory())
                        .minWorkerThreads(cli.minWorkerThreads)
                        .maxWorkerThreads(cli.maxWorkerThreads);

                TServer server = new TThreadPoolServer(sargs);

                String msg = "Starting hive server on port " + cli.port
                             + " with " + cli.minWorkerThreads + " min worker threads and "
                             + cli.maxWorkerThreads + " max worker threads";

                HiveServer.HiveServerHandler.LOG.info(msg);

                // Start Hive Thrift service
                server.serve();

            } catch (Exception e) {
                log.error("Hive server initialization failed..", e);
            }
        }
    }
}
