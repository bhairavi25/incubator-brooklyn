package brooklyn.util.math;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.test.FixedLocaleTest;

public class MathFunctionsTest extends FixedLocaleTest {

    @Test
    public void testAdd() {
        Assert.assertEquals(MathFunctions.plus(3).apply(4), (Integer)7);
        Assert.assertEquals(MathFunctions.plus(0.3).apply(0.4).doubleValue(), 0.7, 0.00000001);
    }
    
    @Test
    public void testReadableString() {
        Assert.assertEquals(MathFunctions.readableString(3, 5).apply(0.0123456), "1.23E-2");
    }
    
    @Test
    public void testPercent() {
        Assert.assertEquals(MathFunctions.percent(3).apply(0.0123456), "1.23%");
    }
    
}
