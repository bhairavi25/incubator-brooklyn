package brooklyn.event.feed.jmx;

import java.util.Collections;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class JmxOperationPollConfig<T> extends PollConfig<Object, T, JmxOperationPollConfig<T>>{

    private ObjectName objectName;
    private String operationName;
    private List<String> signature = Collections.emptyList();
    private List<?> params = Collections.emptyList();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public JmxOperationPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        onSuccess((Function)Functions.identity());
    }

    public JmxOperationPollConfig(JmxOperationPollConfig<T> other) {
        super(other);
        this.objectName = other.objectName;
        this.operationName = other.operationName;
        this.signature = other.signature != null ? ImmutableList.copyOf(other.signature) : null;
        this.params = other.params != null ? ImmutableList.copyOf(other.params) : null;
    }

    public ObjectName getObjectName() {
        return objectName;
    }
    
    public String getOperationName() {
        return operationName;
    }
    
    public List<String> getSignature() {
        return signature;
    }
    
    public List<?> getParams() {
        return params;
    }
    
    public JmxOperationPollConfig<T> objectName(ObjectName val) {
        this.objectName = val; return this;
    }
    
    public JmxOperationPollConfig<T> objectName(String val) {
        try {
            return objectName(new ObjectName(val));
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name ("+val+")", e);
        }
    }

    public JmxOperationPollConfig<T> operationName(String val) {
        this.operationName = val; return this;
    }
    
    public JmxOperationPollConfig<T> operationSignature(List<String> val) {
        this.signature = val; return this;
    }
    
    public JmxOperationPollConfig<T> operationParams(List<?> val) {
        this.params = val; return this;
    }

    public List<?> buildOperationIdentity() {
        // FIXME Have a build() method for ensuring signature is set, and making class subsequently immutable?
        return ImmutableList.of(operationName, buildSignature(), params);
    }
    
    private List<String> buildSignature() {
        if (signature != null && signature.size() == params.size()) {
            return signature;
        } else {
            List<String> derivedSignature = Lists.newLinkedList();
            for (Object param : params) {
                Class<?> clazz = (param != null) ? param.getClass() : null;
                String clazzName = (clazz != null) ? 
                         (JmxHelper.CLASSES.containsKey(clazz.getSimpleName()) ? 
                                 JmxHelper.CLASSES.get(clazz.getSimpleName()) : clazz.getName()) : 
                         Object.class.getName();
                derivedSignature.add(clazzName);
            }
            return derivedSignature;
        }
    }
}
