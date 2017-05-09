package fredboat.agent;

import fredboat.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by napster on 01.05.17.
 * <p>
 * Tries to recover the database from a failed state
 */

public class DBConnectionWatchdogAgent extends Thread {

    private static final Logger log = LoggerFactory.getLogger(DBConnectionWatchdogAgent.class);
    private static final int INTERVAL_MILLIS = 5000; // 5 sec

    private boolean shutdown = false;

    private DatabaseManager dbManager;

    public DBConnectionWatchdogAgent(DatabaseManager dbManager) {
        super(DBConnectionWatchdogAgent.class.getSimpleName());
        setDaemon(true);
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        log.info("Started database connection agent");

        while (!shutdown) {
            try {
                sleep(INTERVAL_MILLIS);

                //we have to proactively call this, as it checks the ssh tunnel for connectivity and does a validation
                //query against the DB
                //the ssh tunnel does detect a disconnect, but doesn't provide a callback for that, so we have to check
                //it ourselves
                dbManager.isAvailable();

                //only recover the database from a failed state
                if (dbManager.getState() == DatabaseManager.DatabaseState.FAILED) {
                    log.info("Attempting to recover failed database connection");
                    dbManager.reconnectSSH();
                }
            } catch (Exception e) {
                log.error("Caught an exception while trying to recover database connection!", e);
            }
        }
    }

    public void shutdown() {
        shutdown = true;
    }
}
