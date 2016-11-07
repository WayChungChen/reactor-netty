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

package reactor.ipc.netty.options;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * An adapted global eventLoop handler.
 *
 * @since 0.6
 */
final class DefaultEventLoopSelector extends AtomicLong implements EventLoopSelector {

	static final Logger log = Loggers.getLogger(DefaultEventLoopSelector.class);

	final boolean                         hasNative;
	final String                          prefix;
	final boolean                         daemon;
	final int                             selectCount;
	final int                             workerCount;
	final EventLoopGroup                  serverLoops;
	final EventLoopGroup                  clientLoops;
	final EventLoopGroup                  serverSelectLoops;
	final AtomicReference<EventLoopGroup> cacheNativeClientLoops;
	final AtomicReference<EventLoopGroup> cacheNativeServerLoops;
	final AtomicReference<EventLoopGroup> cacheNativeSelectLoops;

	static ThreadFactory threadFactory(DefaultEventLoopSelector parent, String prefix) {
		return new EventLoopSelectorFactory(parent.daemon,
				parent.prefix + "-" + prefix,
				parent);
	}

	DefaultEventLoopSelector(String prefix, int workerCount, boolean daemon) {
		this(prefix, -1, workerCount, daemon);
	}

	DefaultEventLoopSelector(String prefix,
			int selectCount,
			int workerCount,
			boolean daemon) {

		this.daemon = daemon;
		this.hasNative = SafeEpollDetector.hasEpoll();
		this.workerCount = workerCount;
		this.prefix = prefix;

		this.serverLoops = EventLoopSelector.colocate(new NioEventLoopGroup(workerCount,
				threadFactory(this, "server-nio")));

		this.clientLoops =
				new NioEventLoopGroup(workerCount, threadFactory(this, "client-nio"));

		this.cacheNativeClientLoops = new AtomicReference<>();
		this.cacheNativeServerLoops = new AtomicReference<>();

		if (selectCount == -1) {
			this.selectCount = workerCount;
			this.serverSelectLoops = this.serverLoops;
			this.cacheNativeSelectLoops = this.cacheNativeServerLoops;
		}
		else {
			this.selectCount = selectCount;
			this.serverSelectLoops =
					new NioEventLoopGroup(selectCount, threadFactory(this, "select-nio"));
			this.cacheNativeSelectLoops = new AtomicReference<>();
		}

		if (log.isDebugEnabled()) {
			log.debug("Default epoll " + "support : " + hasNative);
		}
	}

	@Override
	public EventLoopGroup onServerSelect(boolean useNative) {
		if (useNative && hasNative) {
			return cacheNativeSelectLoops();
		}
		return serverSelectLoops;
	}

	@Override
	public EventLoopGroup onServer(boolean useNative) {
		if (useNative && hasNative) {
			return cacheNativeServerLoops();
		}
		return serverLoops;
	}

	@Override
	public EventLoopGroup onClient(boolean useNative) {
		if (useNative && hasNative) {
			return cacheNativeClientLoops();
		}
		return clientLoops;
	}

	@Override
	public boolean preferNative() {
		return hasNative;
	}

	EventLoopGroup cacheNativeSelectLoops() {
		if (cacheNativeSelectLoops == cacheNativeServerLoops) {
			return cacheNativeServerLoops();
		}

		EventLoopGroup eventLoopGroup = cacheNativeSelectLoops.get();
		if (null == eventLoopGroup) {
			EventLoopGroup newEventLoopGroup = SafeEpollDetector.newEventLoopGroup(
					selectCount,
					threadFactory(this, "select-epoll"));
			if (!cacheNativeSelectLoops.compareAndSet(null, newEventLoopGroup)) {
				newEventLoopGroup.shutdownGracefully();
			}
		}
		return eventLoopGroup;
	}

	EventLoopGroup cacheNativeServerLoops() {
		EventLoopGroup eventLoopGroup = cacheNativeServerLoops.get();
		if (null == eventLoopGroup) {
			EventLoopGroup newEventLoopGroup = SafeEpollDetector.newEventLoopGroup(
					workerCount,
					threadFactory(this, "server-epoll"));
			if (!cacheNativeServerLoops.compareAndSet(null, newEventLoopGroup)) {
				newEventLoopGroup.shutdownGracefully();
			}
		}
		return eventLoopGroup;
	}

	EventLoopGroup cacheNativeClientLoops() {
		EventLoopGroup eventLoopGroup = cacheNativeClientLoops.get();
		if (null == eventLoopGroup) {
			EventLoopGroup newEventLoopGroup = SafeEpollDetector.newEventLoopGroup(
					workerCount,
					threadFactory(this, "client-epoll"));
			newEventLoopGroup = EventLoopSelector.colocate(newEventLoopGroup);
			if (!cacheNativeClientLoops.compareAndSet(null, newEventLoopGroup)) {
				newEventLoopGroup.shutdownGracefully();
			}
		}
		return eventLoopGroup;
	}

	final static class EventLoopSelectorFactory implements ThreadFactory {

		final boolean    daemon;
		final AtomicLong counter;
		final String     prefix;

		public EventLoopSelectorFactory(boolean daemon,
				String prefix,
				AtomicLong counter) {
			this.daemon = daemon;
			this.counter = counter;
			this.prefix = prefix;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setDaemon(daemon);
			t.setName(prefix + "-" + counter.incrementAndGet());
			return t;
		}
	}
}
