package org.wso2.carbon.eventbridge.core.receiver;

import org.wso2.carbon.eventbridge.core.beans.Credentials;
import org.wso2.carbon.eventbridge.core.beans.Event;
import org.wso2.carbon.eventbridge.core.beans.EventStreamDefinition;
import org.wso2.carbon.eventbridge.core.engine.Engine;
import org.wso2.carbon.eventbridge.core.subscriber.EventSubscriber;
import org.wso2.carbon.eventbridge.core.exceptions.DifferentStreamDefinitionAlreadyDefinedException;
import org.wso2.carbon.eventbridge.core.exceptions.EventProcessingException;
import org.wso2.carbon.eventbridge.core.exceptions.StreamDefinitionException;
import org.wso2.carbon.eventbridge.core.exceptions.StreamDefinitionNotFoundException;
import org.wso2.carbon.eventbridge.core.streamdefn.StreamDefinitionStore;
import org.wso2.carbon.eventbridge.core.utils.EventBridgeUtils;

import java.util.Collection;
import java.util.List;

/**
 * Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public abstract class AbstractEventReceiver implements EventSubscriber, StreamDefinitionStore {


    @Override
    public EventStreamDefinition getStreamDefinition(Credentials credentials, String streamName, String streamVersion)
            throws StreamDefinitionNotFoundException, StreamDefinitionException {
        String streamId = getEngine().getStreamId(credentials, streamName, streamVersion);
        if (streamId == null) {
            throw new StreamDefinitionNotFoundException("No definitions exist for " + credentials.getUsername()
                    + " for " + EventBridgeUtils.constructStreamKey(streamName, streamVersion));
        }
        return getEngine().getStreamDefinition( credentials, streamId);
    }

    @Override
    public EventStreamDefinition getStreamDefinition(Credentials credentials, String streamId) throws StreamDefinitionNotFoundException, StreamDefinitionException {
        EventStreamDefinition eventStreamDefinition = getEngine().getStreamDefinition(credentials, streamId);
        if (eventStreamDefinition == null) {
            throw new StreamDefinitionNotFoundException("No definitions exist on " + credentials.getUsername() + " for " + streamId);
        }
        return eventStreamDefinition;
    }

    @Override
    public Collection<EventStreamDefinition> getAllStreamDefinitions(Credentials credentials) throws StreamDefinitionException {
        return getEngine().getAllStreamDefinitions(credentials);
    }

    @Override
    public String getStreamId(Credentials credentials, String streamName, String streamVersion) throws StreamDefinitionNotFoundException, StreamDefinitionException {
        String streamId = getEngine().getStreamId(credentials, streamName, streamVersion);
        if (streamId == null) {
            throw new StreamDefinitionNotFoundException("No stream id found for " + streamId + " " + streamVersion);
        }
        return streamId;
    }

    @Override
    public void saveStreamDefinition(Credentials credentials, EventStreamDefinition eventStreamDefinition) throws DifferentStreamDefinitionAlreadyDefinedException, StreamDefinitionException {
        getEngine().saveStreamDefinition(credentials, eventStreamDefinition);
    }

    @Override
    public void receive(Credentials credentials, List<Event> eventList) throws EventProcessingException {
        getEngine().receive(credentials, eventList);
    }

    protected abstract Engine getEngine();
}
