/*
 * Copyright © 2010. Team Lazer Beez (http://teamlazerbeez.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.teamlazerbeez.crm.sf.soap;

import com.teamlazerbeez.crm.sf.core.Id;
import com.teamlazerbeez.crm.sf.soap.jaxwsstub.apex.ApexPortType;
import com.teamlazerbeez.crm.sf.soap.jaxwsstub.metadata.MetadataPortType;
import com.teamlazerbeez.crm.sf.soap.jaxwsstub.partner.Soap;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

@SuppressWarnings("AccessToStaticFieldLockedOnInstance")
@ThreadSafe
final class ConnectionBundleImpl implements ConnectionBundle {

    private static final XLogger logger = XLoggerFactory.getXLogger(ConnectionBundleImpl.class);

    /**
     * Semaphore that all Connections from this bundle use
     */
    @Nonnull
    private final CallSemaphore callSemaphore;

    @Nonnull
    private final BindingRepository bindingRepository;

    private final boolean sandboxOrg;

    /**
     * Used to make sure that new credentials are for the same org. It is set when the first connection is made, and
     * checked thereafter.
     */
    @GuardedBy("this")
    @Nullable
    private Id orgId = null;

    /**
     * username to connect as -- must be set with updateCredentials.
     */
    @GuardedBy("this")
    @Nonnull
    private String username;

    /**
     * user's sf passwd -- must be set with updateCredentials.
     */
    @GuardedBy("this")
    @Nonnull
    private String password;

    @GuardedBy("this")
    private String sessionId;

    /**
     * Null if there is no current session, etc to use.
     */
    @Nullable
    @GuardedBy("this")
    private BindingConfig bindingConfig = null;


    private String salesforceOrgId;
    private String partnerServerUrl;
    private String metadataServerUrl;


    private ConnectionBundleImpl(@Nonnull String username, @Nonnull String password, int maxConcurrentApiCalls,
            @Nonnull BindingRepository bindingRepository, boolean sandboxOrg) {
        this.sandboxOrg = sandboxOrg;
        this.bindingRepository = bindingRepository;
        this.callSemaphore = new CallSemaphore();

        this.updateCredentials(username, password, maxConcurrentApiCalls);
    }

    public ConnectionBundleImpl(String salesforceOrgId, String sessionId, String partnerServerUrl, String metadataServerUrl, String username, int maxConcurrentApiCalls, BindingRepository bindingRepository, boolean sandboxOrg) {
        this.sandboxOrg = sandboxOrg;
        this.bindingRepository = bindingRepository;
        this.callSemaphore = new CallSemaphore();

        this.updateSessionId(sessionId, maxConcurrentApiCalls);

        this.salesforceOrgId = salesforceOrgId;
        this.orgId = new Id(salesforceOrgId);
        this.partnerServerUrl = partnerServerUrl;
        this.metadataServerUrl = metadataServerUrl;
        this.username = username;
    }

    /**
     * Get a new ConnectionBundle for a standard SF org (not a sandbox).
     *
     * @param bindingRepository     the repository to use for bindings
     * @param username              the username to log in with
     * @param password              the password to log in with
     * @param maxConcurrentApiCalls the maximum number of api calls that can be made concurrently
     *
     * @return a fully configured ConnectionBundle
     */
    static ConnectionBundleImpl getNew(@Nonnull BindingRepository bindingRepository, @Nonnull String username,
            @Nonnull String password, int maxConcurrentApiCalls) {

        return new ConnectionBundleImpl(username, password, maxConcurrentApiCalls,
                bindingRepository, false);
    }

    static ConnectionBundleImpl getNew(BindingRepository bindingRepository, String salesforceOrgId,
            String sessionId, String partnerServerUrl, String metadataServerUrl, String username, int maxConcurrentApiCalls) {
        return new ConnectionBundleImpl(salesforceOrgId, sessionId, partnerServerUrl, metadataServerUrl, username, maxConcurrentApiCalls,
                bindingRepository, false);
    }

    static ConnectionBundleImpl getNewForSandbox(BindingRepository bindingRepository, String salesforceOrgId,
            String sessionId, String partnerServerUrl, String metadataServerUrl, String username, int maxConcurrentApiCalls) {
        return new ConnectionBundleImpl(salesforceOrgId, sessionId, partnerServerUrl, metadataServerUrl, username, maxConcurrentApiCalls,
                bindingRepository, true);
    }



    /**
     * Get a new ConnectionBundle for a sandbox SF org.
     *
     * @param bindingRepository     the repository to use for bindings
     * @param username              the username to log in with
     * @param password              the password to log in with
     * @param maxConcurrentApiCalls the maximum number of api calls that can be made concurrently
     *
     * @return a new ConnectionBundle for a sandbox org
     *
     * @see ConnectionBundleImpl#ConnectionBundleImpl(String, String, int, BindingRepository, boolean)
     */
    static ConnectionBundleImpl getNewForSandbox(@Nonnull BindingRepository bindingRepository,
            @Nonnull String username, @Nonnull String password, int maxConcurrentApiCalls) {

        return new ConnectionBundleImpl(username, password, maxConcurrentApiCalls, bindingRepository,
                true);
    }

    synchronized void updateCredentials(@Nonnull String newUsername, @Nonnull String newPassword,
            int maxConcurrentApiCalls) {
        if (newUsername == null) {
            throw new NullPointerException("Username can't be null");
        }

        if (newPassword == null) {
            throw new NullPointerException("Password can't be null");
        }

        logger.trace("Updating maxApiCalls to " + maxConcurrentApiCalls);
        this.callSemaphore.setMaxPermits(maxConcurrentApiCalls);

        if (newUsername.equals(this.username) && newPassword.equals(this.password)) {
            logger.trace("Got new credentials that were equal to the old credentials");
        } else {
            logger.trace("Updating username to <" + newUsername + "> from <" + this.username + ">");

            this.bindingConfig = null;

            this.username = newUsername;
            this.password = newPassword;
        }
    }

    synchronized void updateSessionId(String sessionId, int maxConcurrentApiCalls) {
        if(sessionId == null){
            throw new NullPointerException("SessionId can't be null");
        }
        logger.trace("Updating maxApiCalls to " + maxConcurrentApiCalls);
        this.callSemaphore.setMaxPermits(maxConcurrentApiCalls);

        if (sessionId.equals(this.sessionId)) {
            logger.trace("Got new sessionId that was equal to the old sessionId");
        } else {
            logger.trace("Updating sessionId to <" + sessionId + "> from <" + this.sessionId + ">");

            this.bindingConfig = null;
            this.sessionId = sessionId;
        }

    }


    @Nonnull
    @Override
    public synchronized PartnerConnection getPartnerConnection() {
        // com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump = true;
        return PartnerConnectionImpl.getNew(this.callSemaphore, this);
    }

    @Nonnull
    synchronized String getUsername() {
        return this.username;
    }

    /**
     * @return a metadata connection
     */
    @Nonnull
    @Override
    public synchronized MetadataConnection getMetadataConnection() {
        return new MetadataConnectionImpl(this.callSemaphore, this);
    }

    @Nonnull
    @Override
    public synchronized ApexConnection getApexConnection() {
        return new ApexConnectionImpl(this.callSemaphore, this);
    }

    @Nonnull
    synchronized ConfiguredBinding<MetadataPortType> getMetadataBinding() throws ApiException {
        final BindingConfig data = this.getBindingConfig();
        return new ConfiguredBinding<MetadataPortType>(this.bindingRepository.getMetadataBinding(data), data);
    }

    synchronized void acceptReleasedMetadataBinding(@Nonnull MetadataPortType binding) {
        this.bindingRepository.releaseMetadataBinding(binding);
    }

    @Nonnull
    synchronized ConfiguredBinding<ApexPortType> getApexBinding() throws ApiException {
        final BindingConfig data = this.getBindingConfig();
        return new ConfiguredBinding<ApexPortType>(this.bindingRepository.getApexBinding(data), data);
    }

    synchronized void acceptReleasedApexBinding(@Nonnull ApexPortType binding) {
        this.bindingRepository.releaseApexBinding(binding);
    }

    /**
     * @return a configured binding ready for use by a PartnerConnectionImpl
     *
     * @throws ApiException if this requires a login which then fails
     */
    @Nonnull
    synchronized ConfiguredBinding<Soap> getPartnerBinding() throws ApiException {
        final BindingConfig data = getBindingConfig();
        return new ConfiguredBinding<Soap>(this.bindingRepository.getPartnerBinding(data), data);
    }

    synchronized void acceptReleasedPartnerBinding(@Nonnull Soap binding) {
        this.bindingRepository.releasePartnerBinding(binding);
    }

    /**
     * Clears the stored session id and sets a new one.
     *
     * @throws ApiException if the attempt to get a new session failed
     */
    synchronized void reportBadSessionId() throws ApiException {
        this.bindingConfig = null;
        logger.info("Attempting to get new session id");
        this.getBindingConfig();
    }

    /**
     * Get the current BindingConfig, creating one if necessary.
     *
     * @return a valid BindingConfig object for this org
     *
     * @throws ApiException if a login was required but was unsuccessful
     */
    @Override
    @Nonnull
    public synchronized BindingConfig getBindingConfig() throws ApiException {
        if (this.bindingConfig == null) {
            if(this.password == null){
                if(sessionId == null){
                    throw new IllegalStateException("If not supplied a password, the sessionId must be configured by calling code");
                }

                this.bindingConfig = new BindingConfig(this.orgId, this.sessionId, this.partnerServerUrl, this.metadataServerUrl, this.username);
            } else{
                // need to login to get a session id

                BindingConfig newBindingConfig;

                newBindingConfig = this.bindingRepository
                        .getBindingConfigData(this.username, this.password, this.callSemaphore, this.sandboxOrg);
                String sessionId = newBindingConfig.getSessionId();

                // never been set -- this is the first connection
                if (this.orgId == null) {
                    this.orgId = newBindingConfig.getOrgId();
                    logger.trace("Setting the bundle's org id to <" + this.orgId + "> from its first connection");
                } else {
                    // if the connections org id isn't what has been previously set, explode
                    if (!this.orgId.equals(newBindingConfig.getOrgId())) {
                        throw new IllegalStateException(
                                "Somehow got a binding with a different organization Id: expected <" + this.orgId +
                                        ">, got <" + newBindingConfig.getOrgId() +
                                        ">. Did you update the credentials to those of a different org?");
                    }
                }

                logger.trace("User " + this.username + ", session id " + sessionId + " on metadata server" +
                        newBindingConfig.getMetadataServerUrl());

                this.bindingConfig = newBindingConfig;
            }
        }

        // we now know there is at least one valid session id

        return this.bindingConfig;
    }
}