/*
* Copyright 2012, CMM, University of Queensland.
*
* This file is part of AclsLib.
*
* AclsLib is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* AclsLib is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with AclsLib. If not, see <http://www.gnu.org/licenses/>.
*/

package au.edu.uq.cmm.aclslib.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Service class that extends this base class will inherit infrastructure to
 * run the service in a thread.  Additional infrastructure will automatically 
 * restart the service thread if it dies with an unhandled exception.
 * The Service class merely needs to implement the {@link Runnable#run()} 
 * method.
 * <p>
 * The restart behavior can be customized by the subclass supplying a
 * {@link RestartDecider} instance in the constructor.
 * <p>
 * Note that in the current implementation, the service goes into the STARTED
 * state without waiting for the actual service thread to reach any particular 
 * state.  The {@link #startStartup()} method blocks until that happens.
 * 
 * @author scrawley
 */
public abstract class MonitoredThreadServiceBase implements Service, Runnable {
    private static final Logger LOG = 
            LoggerFactory.getLogger(MonitoredThreadServiceBase.class);
    
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
                        state = State.STOPPED;
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
        synchronized (lock) {
            if (monitorThread != null && monitorThread.isAlive()) {
                state = State.STARTED;
                LOG.info("Already running");
                return;
            }
            LOG.info("Starting up");
            final Monitor monitor = new Monitor();
            monitorThread = new Thread(monitor);
            monitorThread.setDaemon(true);
            monitorThread.setUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                        public void uncaughtException(Thread t, Throwable ex) {
                            LOG.error("Monitor thread died!", ex);
                            synchronized (lock) {
                                monitor.interruptServiceThread();
                                state = State.FAILED;
                            }
                        }
            });
            state = State.STARTED;
            monitorThread.start();
        }
        LOG.info("Startup done");
    }
    
    public void startStartup() throws ServiceException {
        startup();
    }

    public final void shutdown() {
        final Thread m;
        synchronized (lock) {
            if (monitorThread == null) {
                state = State.STOPPED;
                lock.notifyAll();
                LOG.info("Already shut down");
                return;
            }
            LOG.info("Shutting down");
            state = State.STOPPING;
            monitorThread.interrupt();
            m = monitorThread;
        }
        try {
            m.join();
            synchronized (lock) {
                state = State.STOPPED;
                monitorThread = null;
                lock.notifyAll();
            }
            LOG.info("Shutdown completed");
        } catch (InterruptedException ex) {
            LOG.info("Shutdown interrupted");
            Thread.currentThread().interrupt();
        }
    }

    public void startShutdown() throws ServiceException {
        final Thread m;
        synchronized (lock) {
            if (monitorThread == null) {
                state = State.STOPPED;
                lock.notifyAll();
                LOG.info("Already shut down");
                return;
            }
            LOG.info("Shutting down");
            state = State.STOPPING;
            monitorThread.interrupt();
            m = monitorThread;
        }
        new Thread(new Runnable(){
            public void run() {
                try {
                    m.join();
                    synchronized (lock) {
                        state = State.STOPPED;
                        monitorThread = null;
                        lock.notifyAll();
                    }
                    LOG.info("Shutdown completed");
                } catch (InterruptedException ex) {
                    LOG.info("Shutdown interrupted");
                    Thread.currentThread().interrupt();
                }
            }}).start();
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
