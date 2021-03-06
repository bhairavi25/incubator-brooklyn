package brooklyn.entity.database.rubyrep;

import java.net.URI;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.util.time.Duration;

public class RubyRepNodeImpl extends SoftwareProcessImpl implements RubyRepNode {

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }

    /**
     * Set the database {@link DatastoreCommon#DATASTORE_URL urls} as attributes when they become available on the entities.
     */
    @Override
    protected void preStart() {
        super.preStart();

        DatastoreCommon leftNode = getConfig(LEFT_DATABASE);
        if (leftNode != null) {
            setAttribute(LEFT_DATASTORE_URL, Entities.submit(this, DependentConfiguration.attributeWhenReady(leftNode, DatastoreCommon.DATASTORE_URL)).getUnchecked(getDatabaseStartupDelay()));
        }

        DatastoreCommon rightNode = getConfig(RIGHT_DATABASE);
        if (rightNode != null) {
            setAttribute(RIGHT_DATASTORE_URL, Entities.submit(this, DependentConfiguration.attributeWhenReady(rightNode, DatastoreCommon.DATASTORE_URL)).getUnchecked(getDatabaseStartupDelay()));
        }
    }

    @Override
    public Class<?> getDriverInterface() {
        return RubyRepDriver.class;
    }

    public Duration getDatabaseStartupDelay() {
        return Duration.seconds(getConfig(DATABASE_STARTUP_TIMEOUT));
    }

    // Accessors used in freemarker template processing

    public int getReplicationInterval() {
        return getConfig(REPLICATION_INTERVAL);
    }
    
    public String getTableRegex() {
        return getConfig(TABLE_REGEXP);
    }
    
    public URI getLeftDatabaseUrl() {
        return URI.create(getAttribute(LEFT_DATASTORE_URL));
    }
    
    public String getLeftDatabaseName() {
        return getConfig(LEFT_DATABASE_NAME);
    }

    public String getLeftUsername() {
        return getConfig(LEFT_USERNAME);
    }

    public String getLeftPassword() {
        return getConfig(LEFT_PASSWORD);
    }

    public URI getRightDatabaseUrl() {
        return URI.create(getAttribute(RIGHT_DATASTORE_URL));
    }

    public String getRightDatabaseName() {
        return getConfig(RIGHT_DATABASE_NAME);
    }

    public String getRightUsername() {
        return getConfig(RIGHT_USERNAME);
    }

    public String getRightPassword() {
        return getConfig(RIGHT_PASSWORD);
    }
}
