package com.hazelcast.map.impl.querycache;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.QueryCacheConfig;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.QueryCache;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.TruePredicate;
import com.hazelcast.test.AssertTask;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

import static com.hazelcast.map.impl.querycache.AbstractQueryCacheTestSupport.getMap;
import static com.hazelcast.spi.properties.GroupProperty.PARTITION_COUNT;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class QueryCacheCoalescingTest extends HazelcastTestSupport {

    @SuppressWarnings("unchecked")
    private static final Predicate<Integer, Integer> TRUE_PREDICATE = TruePredicate.INSTANCE;

    @Test
    public void testCoalescingModeWorks() {
        String mapName = randomString();
        String cacheName = randomString();

        Config config = getConfig(mapName, cacheName);
        HazelcastInstance node = createHazelcastInstance(config);
        IMap<Integer, Integer> map = getMap(node, mapName);

        final CountDownLatch updateEventCount = new CountDownLatch(1);
        final QueryCache<Integer, Integer> cache = map.getQueryCache(cacheName, TRUE_PREDICATE, true);
        cache.addEntryListener(new EntryUpdatedListener() {
            @Override
            public void entryUpdated(EntryEvent event) {
                updateEventCount.countDown();
            }
        }, false);

        for (int i = 0; i < 100; i++) {
            map.put(i, i);
        }

        // update same key to control whether coalescing kicks in.
        for (int i = 0; i < 500; i++) {
            map.put(0, i);
        }

        assertTrueEventually(new AssertTask() {
            @Override
            public void run() throws Exception {
                assertEquals(100, cache.size());
            }
        });
        assertOpenEventually(updateEventCount);
    }

    private Config getConfig(String mapName, String cacheName) {
        Config config = new Config();
        config.setProperty(PARTITION_COUNT.getName(), "1");
        MapConfig mapConfig = config.getMapConfig(mapName);
        QueryCacheConfig cacheConfig = new QueryCacheConfig(cacheName);
        cacheConfig.setCoalesce(true);
        cacheConfig.setBatchSize(64);
        cacheConfig.setBufferSize(64);
        cacheConfig.setDelaySeconds(3);
        mapConfig.addQueryCacheConfig(cacheConfig);
        return config;
    }
}