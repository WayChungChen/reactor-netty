/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.ipc.netty.udp;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.NetUtil;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.NettyConnector;
import reactor.ipc.netty.NettyContext;
import reactor.ipc.netty.channel.ChannelOperations;
import reactor.ipc.netty.channel.ContextHandler;
import reactor.ipc.netty.resources.LoopResources;
import reactor.ipc.netty.options.ClientOptions;
import reactor.ipc.netty.options.NettyOptions;

/**
 * A UDP client connector.
 *
 * @author Stephane Maldini
 */
final public class UdpClient implements NettyConnector<UdpInbound, UdpOutbound> {

	/**
	 * Bind a new UDP client to the "loopback" address.
	 * Handlers will run on the same thread they have beem receiving IO events.
	 * <p> The type of emitted data or received data is {@link ByteBuf}
	 *
	 * @return a new {@link UdpClient}
	 */
	public static UdpClient create() {
		return create(NetUtil.LOCALHOST.getHostAddress());
	}

	/**
	 * Bind a new UDP client to the given bind address.
	 * Handlers will run on the same thread they have beem receiving IO events.
	 * <p> The type of emitted data or received data is {@link ByteBuf}
	 *
	 * @param bindAddress bind address (e.g. "127.0.0.1") to create the client on the
	 * passed port
	 *
	 * @return a new {@link UdpClient}
	 */
	public static UdpClient create(String bindAddress) {
		return create(bindAddress, NettyOptions.DEFAULT_PORT);
	}

	/**
	 * Bind a new UDP client to the "loopback" address and specified port.
	 * Handlers will run on the same thread they have beem receiving IO events.
	 * <p> The type of emitted data or received data is {@link ByteBuf}
	 *
	 * @param port the port to listen on the passed bind address
	 *
	 * @return a new {@link UdpClient}
	 */
	public static UdpClient create(int port) {
		return create(NetUtil.LOCALHOST.getHostAddress(), port);
	}

	/**
	 * Bind a new UDP client to the given bind address and port.
	 * Handlers will run on the same thread they have beem receiving IO events.
	 * <p> The type of emitted data or received data is {@link ByteBuf}
	 *
	 * @param port the port to listen on the passed bind address
	 * @param bindAddress bind address (e.g. "127.0.0.1") to create the client on the
	 * passed port
	 *
	 * @return a new {@link UdpClient}
	 */
	public static UdpClient create(String bindAddress, int port) {
		return create(opts -> opts.connect(bindAddress, port));
	}

	/**
	 * Bind a new UDP client to the given bind address and port.
	 * Handlers will run on the same thread they have beem receiving IO events.
	 * <p> The type of emitted data or received data is {@link ByteBuf}
	 *
	 * @param options the configurator
	 *
	 * @return a new {@link UdpClient}
	 */
	public static UdpClient create(Consumer<? super ClientOptions> options) {
		Objects.requireNonNull(options, "options");
		UdpClientOptions clientOptions = new UdpClientOptions();
		clientOptions.loopResources(DEFAULT_UDP_LOOPS);
		options.accept(clientOptions);
		return new UdpClient(clientOptions.duplicate());
	}

	final UdpClientOptions options;

	UdpClient(UdpClientOptions options) {
		this.options = Objects.requireNonNull(options, "options");
	}

	@Override
	public Mono<? extends NettyContext> newHandler(BiFunction<? super UdpInbound, ? super UdpOutbound, ? extends Publisher<Void>> handler) {
		final BiFunction<? super UdpInbound, ? super UdpOutbound, ? extends Publisher<Void>>
				targetHandler =
				null == handler ? ChannelOperations.noopHandler() : handler;

		return Mono.create(sink -> {
			Bootstrap b = options.get();
			ContextHandler<DatagramChannel> c = doHandler(targetHandler, sink);
			b.handler(c);
			c.setFuture(b.bind());
		});
	}

	/**
	 * Create a {@link ContextHandler} for {@link Bootstrap#handler()}
	 *
	 * @param handler user provided in/out handler
	 * @param sink user provided bind handler
	 *
	 * @return a new {@link ContextHandler}
	 */
	protected ContextHandler<DatagramChannel> doHandler(BiFunction<? super UdpInbound, ? super UdpOutbound, ? extends Publisher<Void>> handler,
			MonoSink<NettyContext> sink) {
		return ContextHandler.newClientContext(sink,
				options,
				loggingHandler,
				false,
				(ch, c, msg) -> UdpOperations.bind(ch, handler, c));
	}


	static final int DEFAULT_UDP_THREAD_COUNT = Integer.parseInt(System.getProperty(
			"reactor.udp.ioThreadCount",
			"" + Schedulers.DEFAULT_POOL_SIZE));

	static final LoggingHandler loggingHandler = new LoggingHandler(UdpClient.class);

	static final LoopResources DEFAULT_UDP_LOOPS =
			LoopResources.create("udp", DEFAULT_UDP_THREAD_COUNT, true);
}
