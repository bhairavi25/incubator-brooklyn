package brooklyn.policy.resizing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.List

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import com.google.common.collect.ImmutableMap

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.trait.Resizable
import brooklyn.policy.loadbalancing.BalanceableWorkerPool
import brooklyn.policy.loadbalancing.MockContainerEntity
import brooklyn.test.entity.TestCluster
import brooklyn.util.internal.TimeExtras


class ResizingPolicyTest {
    
    /**
     * Test class for providing a Resizable LocallyManagedEntity for policy testing
     * It is hooked up to a TestCluster that can be used to make assertions against
     */
    public class LocallyResizableEntity extends LocallyManagedEntity implements Resizable {
        List<Integer> sizes = []
        TestCluster cluster
        public LocallyResizableEntity (TestCluster tc) { this.cluster = tc }
        Integer resize(Integer newSize) { Thread.sleep(resizeSleepTime); sizes.add(newSize); cluster.size = newSize }
        Integer getCurrentSize() { return cluster.size }
        String toString() { return getDisplayName() }
    }
    
    
    private static long TIMEOUT_MS = 5000
    private static long SHORT_WAIT_MS = 250
    
    ResizingPolicy policy
    TestCluster cluster
    LocallyResizableEntity resizable
    long resizeSleepTime
    static { TimeExtras.init() }
    
    
    @BeforeMethod()
    public void before() {
        resizeSleepTime = 0
        policy = new ResizingPolicy([:])
        cluster = new TestCluster(1)
        resizable = new LocallyResizableEntity(cluster)
        resizable.addPolicy(policy)
    }

    @Test
    public void testShrinkColdPool() {
        resizable.resize(4)
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 30L, 4*10L, 4*20L))
        
        // expect pool to shrink to 3 (i.e. maximum to have >= 40 per container)
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 3) }
    }
    
    @Test
    public void testShrinkColdPoolRoundsUpDesiredNumberOfContainers() {
        resizable.resize(4)
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 1L, 4*10L, 4*20L))
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
    }
    
    @Test
    public void testGrowHotPool() {
        resizable.resize(2)
        resizable.emit(BalanceableWorkerPool.POOL_HOT, message(2, 41L, 2*10L, 2*20L))
        
        // expect pool to grow to 3 (i.e. minimum to have <= 80 per container)
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 3) }
    }
    
    @Test
    public void testNeverShrinkBelowMinimum() {
        resizable.removePolicy(policy)
        policy = new ResizingPolicy([minPoolSize:2])
        resizable.addPolicy(policy)
        
        resizable.resize(4)
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 0L, 4*10L, 4*20L))
        
        // expect pool to shrink only to the minimum
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 2) }
    }
    
    @Test
    public void testNeverGrowAboveMaximmum() {
        resizable.removePolicy(policy)
        policy = new ResizingPolicy([maxPoolSize:5])
        resizable.addPolicy(policy)
        
        resizable.resize(4)
        resizable.emit(BalanceableWorkerPool.POOL_HOT, message(4, 1000000L, 4*10L, 4*20L))
        
        // expect pool to grow only to the maximum
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 5) }
    }
    
    @Test
    public void testNeverGrowColdPool() {
        resizable.resize(2)
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(2, 1000L, 2*10L, 2*20L))
        
        Thread.sleep(SHORT_WAIT_MS)
        assertEquals(resizable.currentSize, 2)
    }
    
    @Test
    public void testNeverShrinkHotPool() {
        resizeSleepTime = 0
        resizable.resize(2)
        resizable.emit(BalanceableWorkerPool.POOL_HOT, message(2, 0L, 2*10L, 2*20L))
        
        // if had been a POOL_COLD, would have shrunk to 3
        Thread.sleep(SHORT_WAIT_MS)
        assertEquals(resizable.currentSize, 2)
    }
    
    @Test
    public void testConcurrentShrinkShrink() {
        resizeSleepTime = 250
        resizable.resize(4)
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 30L, 4*10L, 4*20L))
        // would cause pool to shrink to 3
        
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 1L, 4*10L, 4*20L))
        // now expect pool to shrink to 1
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
    }
    
    @Test
    public void testConcurrentGrowGrow() {
        resizeSleepTime = 250
        resizable.resize(2)
        resizable.emit(BalanceableWorkerPool.POOL_HOT, message(2, 41L, 2*10L, 2*20L))
        // would cause pool to grow to 3
        
        resizable.emit(BalanceableWorkerPool.POOL_HOT, message(2, 81L, 2*10L, 2*20L))
        // now expect pool to grow to 5
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 5) }
    }
    
    @Test
    public void testConcurrentGrowShrink() {
        resizeSleepTime = 250
        resizable.resize(2)
        resizable.emit(BalanceableWorkerPool.POOL_HOT, message(2, 81L, 2*10L, 2*20L))
        // would cause pool to grow to 5
        
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(2, 1L, 2*10L, 2*20L))
        // now expect pool to shrink to 1
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
    }
    
    @Test
    public void testConcurrentShrinkGrow() {
        resizeSleepTime = 250
        resizable.resize(4)
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 1L, 4*10L, 4*20L))
        // would cause pool to shrink to 1
        
        resizable.emit(BalanceableWorkerPool.POOL_HOT, message(4, 81L, 4*10L, 4*20L))
        // now expect pool to grow to 5
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 5) }
    }
    
    @Test
    public void testRepeatedQueuedResizeTakesLatestValueRatherThanIntermediateValues() {
        // TODO is this too time sensitive? the resize takes only 250ms so if it finishes before the next emit we'd also see size=2
        resizeSleepTime = 500
        resizable.resize(4)
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 30L, 4*10L, 4*20L)) // shrink to 3
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 20L, 4*10L, 4*20L)) // shrink to 2
        resizable.emit(BalanceableWorkerPool.POOL_COLD, message(4, 10L, 4*10L, 4*20L)) // shrink to 1
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
        assertEquals(resizable.sizes, [4, 3, 1])
    }
    

    static Map<String, Object> message(int currentSize, double currentWorkrate, double lowThreshold, double highThreshold) {
        return ImmutableMap.of(
            ResizingPolicy.POOL_CURRENT_SIZE_KEY, currentSize,
            ResizingPolicy.POOL_CURRENT_WORKRATE_KEY, currentWorkrate,
            ResizingPolicy.POOL_LOW_THRESHOLD_KEY, lowThreshold,
            ResizingPolicy.POOL_HIGH_THRESHOLD_KEY, highThreshold)
    }
    
}