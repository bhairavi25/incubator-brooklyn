package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.File;
import java.util.concurrent.Executors;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class MementoFileWriterTest {

    private static final Duration TIMEOUT = Duration.TEN_SECONDS;
    
    private File file;
    private ListeningExecutorService executor;
    private MementoSerializer<String> serializer;
    private MementoFileWriter<String> writer;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        file = Os.newTempFile(getClass(), "txt");
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        serializer = new MementoSerializer<String>() {
            @Override public String toString(String memento) {
                return memento;
            }
            @Override public String fromString(String string) {
                return string;
            }
            @Override public void setLookupContext(LookupContext lookupContext) {
            }
            @Override public void unsetLookupContext() {
            }
        };
        writer = new MementoFileWriter<String>(file, executor, serializer);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        if (file != null) file.delete();
        
    }

    @Test
    public void testWritesFile() throws Exception {
        writer.write("abc");
        writer.waitForWriteCompleted(TIMEOUT);
        
        String fromfile = Files.asCharSource(file, Charsets.UTF_8).read();
        assertEquals(fromfile, "abc");
    }
    
    @Test
    public void testWriteBacklogThenDeleteWillLeaveFileDeleted() throws Exception {
        String big = makeBigString(100000);
        
        writer.write(big);
        writer.write(big);
        writer.delete();
        
        writer.waitForWriteCompleted(TIMEOUT);
        assertFalse(file.exists());
    }
    
    private String makeBigString(int size) {
        return com.google.common.base.Strings.repeat("x", size);
    }
}
