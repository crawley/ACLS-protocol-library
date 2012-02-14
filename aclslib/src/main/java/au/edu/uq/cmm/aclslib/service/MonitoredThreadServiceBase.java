package au.edu.uq.cmm.aclslib.service;

import org.apache.log4j.Logger;

/**
 * A Service class that extends this base class will inherit infrastructure to
 * run the service in a thread.  Additional infrastructure will automatically 
 * restart the service thread if it dies with an unhandled exception.
 * The Service class merely needs to implement the {@link Runnable#run()} 
 * method.
 * <p>
 * The restart behavior can be customized by the subclass supplying a
 * {@link RestartDecider} instance in the constructor.
 * 
 * @author scrawley
 */
public abstract class MonitoredThreadServiceBase implements Service, Runnable {
    private static final Logger LOG = Logger.getLogger(MonitoredThreadServiceBase.class);
    
    private class Monitor implements Runnable {
        private Throwable lastException;
        private Thread serviceThread;
        
        public void run() {
            while (true) {
                serviceThread = new Thread(MonitoredThreadServiceBase.this);
                serviceThread.setDaemon(true);
                serviceThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable ex) {
                        lastException = ex;
                        LOG.error("Service thread died", ex);
                    }
                });
                lastException = null;
                serviceThread.start();
                try {
                    serviceThread.join();
                    if (!restartDecider.isRestartable(lastException)) {
                        LOG.error("Service thread not restartable - bailing out");
                        synchronized (lock) {
                            state = State.FAILED;
                        }
                        break;
                    }
                } catch (InterruptedException ex) {
                    LOG.info("Monitor thread got the interrupt");
                    serviceThread.interrupt();
                    unblock();
                    try {
                        LOG.info("Interrupted service thread");
                        serviceThread.join();
                    } catch (InterruptedException ex2) {
                        LOG.error("Monitor thread interrupted while waiting " +
                        		"for service thread to finish", ex);
                    }
                    synchronized (lock) {
                        state = State.SHUT_DOWN;
                    }
                    LOG.info("Finished interrupt processing");
                    break;
                }
            }
        }

        private void interruptServiceThread() {
            if (serviceThread != null) {
                serviceThread.interrupt();
            }
        }
    }

    private State state = State.INITIAL;
    private Thread monitorThread;
    private RestartDecider restartDecider;
    private final Object lock = new Object();
    
    /**
     * Instantiate using a default RestartDecider.
     */
    protected MonitoredThreadServiceBase() {
        this(new DefaultRestartDecider());
    }
    
    /**
     * Instantiate using a supplied RestartDecider
     * @param restartDecider
     */
    protected MonitoredThreadServiceBase(RestartDecider restartDecider) {
        this.restartDecider = restartDecider;
    }
    
    /**
     * This is called by the monitor thread to unblock the service
     * thread after interrupting it.
     */
    protected void unblock() {
        // Do nothing by default.
    }

    public final void startup() {
        LOG.info("Starting up");
        synchronized (lock) {
            if (monitorThread != null && monitorThread.isAlive()) {
                state = State.RUNNING;
                LOG.info("Already running");
                return;
            }
            final Monitor monitor = new Monitor();
            monitorThread = new Thread(monitor);
            monitorThread.setDaemon(true);
            monitorThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable ex) {
                    LOG.error("Monitor thread died!", ex);
                    synchronized (lock) {
                        monitor.interruptServiceThread();
                        state = State.FAILED;
                    }
                }
            });
            state = State.RUNNING;
            monitorThread.start();
        }
        LOG.info("Startup done");
    }

    public final void shutdown() {
        Thread m;
        LOG.info("Shutting down");
        synchronized (lock) {
            if (monitorThread == null) {
                state = State.SHUT_DOWN;
                lock.notifyAll();
                LOG.info("Already shut down");
                return;
            }
            monitorThread.interrupt();
            m = monitorThread;
        }
        try {
            m.join();
            synchronized (lock) {
                monitorThread = null;
                state = State.SHUT_DOWN;
                lock.notifyAll();
            }
            LOG.info("Shutdown completed");
        } catch (InterruptedException ex) {
            LOG.info("Shutdown interrupted");
            Thread.currentThread().interrupt();
        }
    }

    public final void awaitShutdown() throws InterruptedException {
        synchronized (lock) {
            while (monitorThread != null) {
                lock.wait();
            }
        }
    }

    public final State getState() {
        synchronized (lock) {
            return state;
        }
    }
}
