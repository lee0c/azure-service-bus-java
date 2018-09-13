/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.servicebus.amqp;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.servicebus.primitives.TransportType;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.SslDomain;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.impl.TransportInternal;
import org.apache.qpid.proton.reactor.Handshaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.servicebus.primitives.ClientConstants;
import com.microsoft.azure.servicebus.primitives.StringUtil;

// ServiceBus <-> ProtonReactor interaction handles all
// amqp_connection/transport related events from reactor
public class ConnectionHandler extends BaseHandler
{
	private static final Logger TRACE_LOGGER = LoggerFactory.getLogger(ConnectionHandler.class);
	private final IAmqpConnection messagingFactory;

	protected ConnectionHandler(final IAmqpConnection messagingFactory)
	{
		add(new Handshaker());
		this.messagingFactory = messagingFactory;
	}

	public static ConnectionHandler create(TransportType transportType, IAmqpConnection messagingFactory)
	{
		switch(transportType) {
			case AMQP_WEB_SOCKETS:
				if (ProxyConnectionHandler.shouldUseProxy()) {
					return new ProxyConnectionHandler(messagingFactory);
				} else {
					return new WebSocketConnectionHandler(messagingFactory);
				}
			case AMQP:
			default:
				return new ConnectionHandler(messagingFactory);
		}
	}
	
	@Override
	public void onConnectionInit(Event event)
	{
		final Connection connection = event.getConnection();
		final String hostName = new StringBuilder(this.messagingFactory.getHostname())
									.append(":")
									.append(String.valueOf(this.getProtocolPort()))
									.toString();
		TRACE_LOGGER.debug("onConnectionInit: hostname:{}", hostName);
		connection.setHostname(hostName);
		connection.setContainer(StringUtil.getShortRandomString());
		
		final Map<Symbol, Object> connectionProperties = new HashMap<Symbol, Object>();
        connectionProperties.put(AmqpConstants.PRODUCT, ClientConstants.PRODUCT_NAME);
        connectionProperties.put(AmqpConstants.VERSION, ClientConstants.CURRENT_JAVACLIENT_VERSION);
        connectionProperties.put(AmqpConstants.PLATFORM, ClientConstants.PLATFORM_INFO);
        connection.setProperties(connectionProperties);
        
		connection.open();
	}

	public void addTransportLayers(final Event event, final TransportInternal transport)
	{
		final SslDomain domain = makeDomain(SslDomain.Mode.CLIENT);
		transport.ssl(domain);
	}

	public String getOutboundSocketHostName()
	{
		return messagingFactory.getHostname();
	}

	public int getOutboundSocketPort()
	{
		return this.getProtocolPort();
	}

	protected int getProtocolPort()
	{
		return ClientConstants.AMQPS_PORT;
	}

	public int getMaxFrameSize()
	{
		return AmqpConstants.MAX_FRAME_SIZE;
	}

	@Override
	public void onConnectionBound(Event event)
	{
	    TRACE_LOGGER.debug("onConnectionBound: hostname:{}", event.getConnection().getHostname());
		Transport transport = event.getTransport();

		this.addTransportLayers(event, (TransportInternal) transport);

		Sasl sasl = transport.sasl();
		sasl.setMechanisms("ANONYMOUS");
	}

	@Override
	public void onTransportError(Event event)
	{
		ErrorCondition condition = event.getTransport().getCondition();
		if (condition != null)
		{			
			TRACE_LOGGER.warn("Connection.onTransportError: hostname:{}, error:{}", event.getConnection().getHostname(), condition.getDescription());
		}
		else
		{			
			TRACE_LOGGER.warn("Connection.onTransportError: hostname:{}. error:{}", event.getConnection().getHostname(), "no description returned");
		}

		this.messagingFactory.onConnectionError(condition);
		Connection connection = event.getConnection();
		if(connection != null)
		{
		    connection.free();
		}
	}

	@Override
	public void onConnectionRemoteOpen(Event event)
	{		
		TRACE_LOGGER.debug("Connection.onConnectionRemoteOpen: hostname:{}, remotecontainer:{}", event.getConnection().getHostname(), event.getConnection().getRemoteContainer());
		this.messagingFactory.onConnectionOpen();
	}

	@Override
	public void onConnectionRemoteClose(Event event)
	{
		final Connection connection = event.getConnection();
		final ErrorCondition error = connection.getRemoteCondition();
		
		TRACE_LOGGER.debug("onConnectionRemoteClose: hostname:{},errorCondition:{}", connection.getHostname(), error != null ? error.getCondition() + "," + error.getDescription() : null);
		boolean shouldFreeConnection = connection.getLocalState() == EndpointState.CLOSED;		
		this.messagingFactory.onConnectionError(error);
		if(shouldFreeConnection)
		{
		    connection.free();
		}
	}
	
	@Override
    public void onConnectionFinal(Event event) {
	    TRACE_LOGGER.debug("onConnectionFinal: hostname:{}", event.getConnection().getHostname());
    }
	
	@Override
    public void onConnectionLocalClose(Event event) {
	    Connection connection = event.getConnection();
	    TRACE_LOGGER.debug("onConnectionLocalClose: hostname:{}", connection.getHostname());
	    if(connection.getRemoteState() == EndpointState.CLOSED)
	    {
	        // Service closed it first. In some such cases transport is not unbound and causing a leak.
	        if(connection.getTransport() != null)
	        {
	            connection.getTransport().unbind();
	        }
	        
	        connection.free();
	    }
    }

	private static SslDomain makeDomain(SslDomain.Mode mode)
	{
		SslDomain domain = Proton.sslDomain();
		domain.init(mode);

		// TODO: VERIFY_PEER_NAME support
		domain.setPeerAuthentication(SslDomain.VerifyMode.ANONYMOUS_PEER);
		return domain;
	}
}
