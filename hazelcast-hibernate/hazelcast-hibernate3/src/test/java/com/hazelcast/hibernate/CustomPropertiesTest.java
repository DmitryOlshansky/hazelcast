/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.hibernate;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.HazelcastClientProxy;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.hibernate.entity.DummyEntity;
import com.hazelcast.hibernate.instance.HazelcastAccessor;
import com.hazelcast.hibernate.instance.HazelcastMockInstanceLoader;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.hibernate.cache.CacheException;

import java.util.Date;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class CustomPropertiesTest extends HibernateTestSupport {

    @Test
    public void testNativeClient() throws Exception {

        TestHazelcastFactory factory = new TestHazelcastFactory();
        Config config = new ClasspathXmlConfig("hazelcast-custom.xml");
        HazelcastInstance main = factory.newHazelcastInstance(config);
        Properties props = getDefaultProperties();
        props.remove(CacheEnvironment.CONFIG_FILE_PATH_LEGACY);
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        props.setProperty(CacheEnvironment.USE_NATIVE_CLIENT, "true");
        props.setProperty(CacheEnvironment.NATIVE_CLIENT_GROUP, "dev-custom");
        props.setProperty(CacheEnvironment.NATIVE_CLIENT_PASSWORD, "dev-pass");
        props.setProperty(CacheEnvironment.NATIVE_CLIENT_ADDRESS, "localhost");
        props.setProperty(CacheEnvironment.CONFIG_FILE_PATH,"hazelcast-client-custom.xml");
        HazelcastMockInstanceLoader loader = new HazelcastMockInstanceLoader();
        loader.configure(props);
        loader.setInstanceFactory(factory);
        SessionFactory sf = createSessionFactory(props, loader);
        HazelcastInstance hz = HazelcastAccessor.getHazelcastInstance(sf);
        assertTrue(hz instanceof HazelcastClientProxy);
        assertEquals(1, main.getCluster().getMembers().size());
        HazelcastClientProxy client = (HazelcastClientProxy) hz;
        ClientConfig clientConfig = client.getClientConfig();
        assertEquals("dev-custom", clientConfig.getGroupConfig().getName());
        assertEquals("dev-pass", clientConfig.getGroupConfig().getPassword());
        assertTrue(clientConfig.getNetworkConfig().isSmartRouting());
        assertTrue(clientConfig.getNetworkConfig().isRedoOperation());
        factory.newHazelcastInstance(config);
        assertClusterSizeEventually(2, hz);
        main.shutdown();
        assertClusterSizeEventually(1, hz);
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        session.save(new DummyEntity(1L, "dummy", 0, new Date()));
        tx.commit();
        session.close();
        sf.close();
        factory.shutdownAll();
    }

    @Test
    public void testNamedInstance() {
        TestHazelcastFactory factory = new TestHazelcastFactory();
        Config config = new Config();
        config.setInstanceName("hibernate");
        HazelcastInstance hz = factory.newHazelcastInstance(config);
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        props.put(CacheEnvironment.HAZELCAST_INSTANCE_NAME, "hibernate");
        props.put(CacheEnvironment.SHUTDOWN_ON_STOP, "false");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");

        Configuration configuration = new Configuration();
        configuration.addProperties(props);

        SessionFactory sf = configuration.buildSessionFactory();
        assertTrue(hz.equals(HazelcastAccessor.getHazelcastInstance(sf)));
        sf.close();
        assertTrue(hz.getLifecycleService().isRunning());
        factory.shutdownAll();
    }

    @Test
    public void testHazelcastAccessorReturnsNullIfSecondLevelCacheIsNotHazelcast() {
        Properties props = new Properties();
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");

        Configuration configuration = new Configuration();
        configuration.addProperties(props);
        SessionFactory sf = configuration.buildSessionFactory();

        assertNull(HazelcastAccessor.getHazelcastInstance(sf));

        sf.close();
    }

    @Test(expected = CacheException.class)
    public void testWrongHazelcastConfigurationFilePathShouldThrow() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");

        //we give a non-exist config file address, should fail fast
        props.setProperty("hibernate.cache.hazelcast.configuration_file_path", "non-exist.xml");

        Configuration configuration = new Configuration();
        configuration.addProperties(props);
        SessionFactory sf = configuration.buildSessionFactory();
        sf.close();
    }

    private Properties getDefaultProperties() {
        Properties props = new Properties();
        props.setProperty(Environment.CACHE_REGION_FACTORY, HazelcastCacheRegionFactory.class.getName());
        props.setProperty(CacheEnvironment.CONFIG_FILE_PATH_LEGACY, "hazelcast-custom.xml");
        return props;
    }
}