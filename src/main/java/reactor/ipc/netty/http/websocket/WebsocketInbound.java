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

package reactor.ipc.netty.http.websocket;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import reactor.core.publisher.Flux;
import reactor.ipc.netty.NettyInbound;

/**
 * A websocket framed inbound
 *
 * @author Stephane Maldini
 * @since 0.6
 */
public interface WebsocketInbound extends NettyInbound {

	/**
	 * Turn this {@link WebsocketInbound} into aggregating mode which will only produce
	 * fully formed frame that have been received fragmented.
	 *
	 * Will aggregate up to 65,536 bytes per frame
	 *
	 * @return this inbound
	 */
	default WebsocketInbound aggregateFrames() {
		return aggregateFrames(2 ^ 16);
	}

	/**
	 * Turn this {@link WebsocketInbound} into aggregating mode which will only produce
	 * fully formed frame that have been received fragmented.
	 *
	 * @param maxContentLength the maximum frame length
	 *
	 * @return this inbound
	 */
	default WebsocketInbound aggregateFrames(int maxContentLength) {
		context().addDecoder(new WebSocketFrameAggregator(maxContentLength));
		return this;
	}

	/**
	 * @return a {@link Flux} of {@link WebSocketFrame} formed frame content
	 */
	default Flux<WebSocketFrame> receiveFrames() {
		return receiveObject().ofType(WebSocketFrame.class);
	}
}
