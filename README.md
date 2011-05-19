# Java Mongrel2 Handler

High performance Java Mongrel2 Handler that fully supports all of the
behavior of the reference mongrel2 handler, and both the JSON and
Tnetstrings mongrel2 protocols.

## Dependencies
* JDK 1.5+
* [jzmq, the Java 0mq binding](https://github.com/zeromq/jzmq)
* [(Optional) Jackson [jackson-core.jar, jackson-mapper.jar]](http://jackson.codehaus.org/)

## Chat Example

This is just showing that this handler works the same way as the reference Mongrel2 handler. The behavior is intended to be identical. You can write handlers different languages under the same mongrel2 server. This is just the chat API ported from the Mongrel2 python examples:

```java
package org.mongrel2;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.mongrel2.Handler;
import org.mongrel2.Handler.Connection;
import org.mongrel2.Handler.Request;
// guava collections are just used in example code, not required
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public final class Chat {

  private static final String SENDER_ID = "82209006-86FF-4982-B5EA-D1E29E55D481";
  private static final Connection CONN = Handler.connection(SENDER_ID,
    "tcp://127.0.0.1:9999", "tcp://127.0.0.1:9998");
  private static final ConcurrentMap<String, Object> USERS = Maps.newConcurrentMap();
  
  public static void main(String[] args) {
    
    for (;;) {
      final Request req = CONN.recv();
      
      final Map<String, Object> data = req.getData();
      final Object type = data.get("type");
      if ("join".equals(type)) {
        CONN.deliverJson(req.getSender(), USERS.keySet(), data);
        USERS.put(req.getConnId(), data.get("user"));
        CONN.replyJson(req, ImmutableMap.of(
          "type", "userList",
          "users", USERS.values()
        ));
      } else if ("disconnect".equals(type)) {
        System.out.println("DISCONNECTED" + req.getConnId());
        final Object removedUser = USERS.remove(req.getConnId());
        CONN.deliverJson(req.getSender(), USERS.keySet(),
          ImmutableMap.<String, Object>builder().putAll(data).put("user", removedUser).build());
      } else if (!USERS.containsKey(req.getConnId())) {
        USERS.put(req.getConnId(), data.get("user"));
      } else if ("msg".equals(type)) {
        CONN.deliverJson(req.getSender(), USERS.keySet(), data);
      }
      
      System.out.println("REGISTERED USERS: " + USERS.size());
    }
  }
}
```

## Handler Scaling Recipes

Handlers can be run in 1 or more processes, on one machine or across many machines. Mongrel2, via 0mq will load balance automatically. But how do you scale and parallelize work and background tasks?

This library doesn't enforce any pattern. Here are some basic ones.

### 1. Create a bunch of handler processes

0mq, which is used under the hood, enables this Erlang style of concurrency--just pass messages between processes. The processes aren't "light weight processes", but the advantage is that multiple languages can be used in the same application.

Pros:
* Simple. No worries about thread safety because all data is copied as whole messages.
* Similar to how you would create handlers in most other languages--run a bunch of python, lua, etc handler processes.
* Makes a lot of sense for languages with poor threading capabilities (or lack of safer/easier high level abstractions like Executors).
* Makes a lot of sense for any language to prevent common concurrency bugs caused by sharing mutable state.

Cons:
* The virtual machine takes requires lots of memory compared to most non-vm languages. This makes "many small processes" impossible.
* Doesn't leverage stellar concurrency support in Java (java.util.Concurrent).

If you are not doing much work in the handler (for example, just interacting with a cache), then you probly don't need to spread the work across many processes. A couple process per machine for redundancy should be good. Otherwise, you can scale by starting more processes to a limited extent.

### 2. Use 0mq inproc: endpoints with ZMQ PAIR Sockets to pass messages between threads

There is no need for the user of a Handler to use 0mq directly. But it 0mq is a good "safety first" tool when working with threads.

See: [Multithreading with ØMQ](http://zguide.zeromq.org/page:all#toc38) in the [ØMQ Guide](http://zguide.zeromq.org/page:all)

Pros:
* No worries about thread safety because all data is copied atomically as whole messages.
* Fewer processes to run and manage (more important for Java with that fat VM footprint)
* Again, makes a lot of sense for safety and prevention concurrency bugs owing to mutable state clobbering or locking issues.

Cons:
* 0mq avoids problems caused by sharing data in concurrency by copying. This pro for safety is a small con for performance. (But safety first!)
* Doesn't leverage stellar concurrency support in Java (java.util.Concurrent).

### 3. Use java.util.concurrent and the high-level Executor API

Requests obtained from Connections in this API are immutable and thread safe. You can simply pass them to a worker pool. It's also possible (as of 0mq 2.1.x+) to reply/deliver responses from a different pool. Just don't share mutable state! You avoid the shared data problems and also obviate the need for locking.

Pros:
* No worries about thread safety *if* only immutable data is shared.
* Highest throughput "zero copy" option. Just pass the Request or derived immutable to executors.

Cons:
* Requires high level knowledge of the Executor framework
* Easy to shoot self in foot if you don't know the basics

But the main point is--you should be able to run handlers and spread their work however you like.

Here's an example splitting work across ThreadPoolExecutor workers:

```java
package org.mongrel2;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mongrel2.Handler.Connection;
import org.mongrel2.Handler.Request;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;

public class WorkerPoolThreadsExample {

  private static final String SENDER_ID = "82209006-86FF-4982-B5EA-D1E29E55D481";
  private static final Connection CONN = Handler.connection(SENDER_ID,
    "tcp://127.0.0.1:9999", "tcp://127.0.0.1:9998");

  // Or Executors.cachedThreadPool or configure a ThreadPoolExecutor instance...
  private static final ExecutorService WORKERS = Executors.newFixedThreadPool(10);
  private static final Random RAND = new Random();

  public static void main(final String[] args) {

    // How to handle clean shutdown or CTRL-C
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override public void run() {
        System.out.println("Initiating orderly shutdown of workers & waiting for task to complete");
        // If you want all queued tasks to finish on clean shutdown or CTRL-C
        WORKERS.shutdown();
        System.out.println("Workers fully shut down. Bye.");
        // or maybe you just want to WORKERS.shutdownNow(); if you don't care if
        // queued tasks don't get done.
        Closeables.closeQuietly(CONN);
      }});

    for (;;) { // event loop
      final Request request = CONN.recv();
      WORKERS.execute(new HardWorker(request)); // work done in parellel and possibly queued
    }
  }

  private static final class HardWorker implements Runnable {

    private final Request request; // Immutable Request is safely shared
    HardWorker(Request request) {
      this.request = request;
    }

    @Override public void run() {
      try {
        System.out.println("Starting Heavy Work");
        Thread.sleep(1000); // Do all that heavy work here.
        if (RAND.nextInt(5) == 3) { // But some exceptions may happen :)
          throw new IllegalStateException("Oops!");
        }
        System.out.println("Done with Heavy Work" + request);
        // safe to reply async from another thread.
        CONN.replyJson(request, ImmutableMap.of("success", true));
      } catch (final Exception e) {
        CONN.replyJson(request, ImmutableMap.of("success", false));
        System.err.println("Oh noes!");
      }
    }
  }

}
```

## Documentation
```java
/* Copyright 2011 Armando Singer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mongrel2;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

/**
 * High performance Java Mongrel2 Handler that fully supports all of the behavior
 * of the reference mongrel2 handler, and both the JSON and Tnetstrings mongrel2
 * protocols (including floats).<br><br>
 *
 * Dependencies: Java 1.5+. jzmq, the Java 0mq binding.
 * (Optional) Jackson [jackson-core.jar, jackson-mapper.jar].<br><br>
 * 
 * This implementation is lazy. Headers or body are not parsed unless they are asked for.
 * (Sometimes you just want the presence of a Request to trigger an action.
 * No need to parse anything.) If you are not using the JSON mongrel2 protocol
 * (to pass headers from mongrel2 to the handler), and you are not replying
 * with or delivering JSON, then the Jackson JSON dependency is optional.<br><br>
 * 
 * Requests are deeply immutable and thread safe. You can safely pass them through
 * threaded work pipeline, for example.<br><br>
 * 
 * In addition to laziness, this implementations is very performant and efficient
 * because care is taken to produce as little garbage as possible and to copy as
 * little as possible. 0mq passes whole messages. This is taken into account, so
 * there is no stream-oriented parsing. Rather than processing data as a stream
 * by wrapping in an InputStream wrapper, ByteBuffers, etc., data elements are
 * parsed directly from the 0mq byte array ranges without creating intermediate
 * Strings, holder objects, or copies where possible.<br><br>
 *
 * This handler does not prescribe or enforce an invocation method. You can create many
 * handlers that run in separate processes (many processes on a single machine
 * or few processes but spread out on multiple machines). This is the common scenario
 * in most languages. You can also run a single handler that processes Requests with a
 * "worker" thread pool instead of running many "worker" processes.
 * (More common in Java.)<br><br>
 * 
 * Or you can run background tasks triggered by the handler as separate 0mq processes,
 * or use the excellent java.util.concurrent.Executor framework...etc, etc.<br><br>
 *
 * This handler also does not create another new way to create HTTP requests,
 * responses and headers. If you have really strict requirements to create a valid
 * response for example, you can use Apache HTTPComponents Core, which provides a
 * very rich object model and set of builders (HTTPResponse objects, Header objects,
 * StatusLine, HeaderElement, HeaderGroup, HeaderIterator, HTTPEntity, plenty of
 * Constants for valid reason codes--dozens (hundreds?) of classes to model HTTP.).<br><br>
 * 
 * Or, if you just want a lighter weight way to create Maps required by this handler
 * to create headers, for example, consider using the guava libs. ImmutableMap alone
 * is worth it.<br><br>
 * 
 * Updated on 2011/03/01 to support tnetstrings<br>
 * Updated on 2011/03/16 to support tnetstring floats
 * 
 * @author Armando Singer (armando.singer at gmail dot com)
 * 
 * @version 1.6.1
 *
 * @see <a href="https://github.com/zeromq/jzmq">(Required) jzmq - Java binding for 0MQ</a>
 * @see <a href="http://jackson.codehaus.org/">(Required only if json used) Jackson - High-performance JSON processor</a>
 * @see <a href="http://code.google.com/p/guava-libraries/">(Totally Optional) Guava libraries</a>
 * @see <a href="http://hc.apache.org/httpcomponents-core-ga/index.html">(Totally Optional) Apache HttpCore</a>
 */
public final class Handler {

  private Handler() { }

  // charset for mongrel2 http headers, the connection senderId, uuid, connid
  // & the request senderId, connId, path and some built-in header names
  private static final Charset ASCII = Charset.forName("US-ASCII");

  public static Connection connection(String senderId, String subAddress, String pubAddress) {
    return new Connection(senderId, subAddress, pubAddress);
  }

  public static Connection connection(byte[] senderId, String subAddress, String pubAddress) {
    return new Connection(senderId, subAddress, pubAddress);
  }

  /** The mogrel2 request; instances are deeply immutable and thread safe */
  public static final class Request implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String sender, connId, path;
    private final byte[] msg;
    private final int headerSizeOffset, headerDataOffset, bodySizeOffset;
    private final boolean recvJson;
    private transient Map<String, List<String>> cachedHeaders;

    /** @throws IllegalArgumentException if any request part is invalid */
    private Request(String sender, String connId, String path, byte[] msg,
      int headerSizeOffset, int headerDataOffset, int bodySizeOffset, boolean forceJson) {
      this.sender = checkNotNullOrEmpty(sender, "Invalid request, no sender.");
      this.connId = checkNotNullOrEmpty(connId, "Invalid request, no connId.");
      this.path = checkNotNullOrEmpty(path, "Invalid request, no path");
      this.msg = msg;
      this.headerSizeOffset = headerSizeOffset;
      this.headerDataOffset = headerDataOffset;
      this.bodySizeOffset = bodySizeOffset;
      this.recvJson = forceJson;
    }

    /**
     * This is used for all 0mq message parsing. 0mq only sends and receives byte[],
     * so we take care not convert the message body to a String to prevent unnecessary
     * re-encoding and copying.
     * 
     * Format: UUID ID PATH SIZE:HEADERS,SIZE:BODY,
     */
    static Request parse(byte[] msg, boolean recvJson) {
      checkNotNullOrEmpty(msg, "Invalid request, no message");
      String sender, connId, path;
      sender = connId = path = null; 
      int headerSizeOffset = 0;
      boolean inPrefix = true;
      for (int i = 0; i < msg.length; i++) {
        if (inPrefix && msg[i] == ' ') {
          if (sender == null)
            sender = asciiFromRange(msg, 0, i);
          else if (connId == null)
            connId = asciiFromRange(msg, sender.length() + 1, i);
          else {
            path = asciiFromRange(msg, sender.length() + connId.length() + 2, i);
            headerSizeOffset = i + 1;
            inPrefix = false;
          }
        } else if (msg[i] == ':') {
          final int headerDataOffset = i + 1;
          final int headerSize = TNetstring.parseSize(msg, headerSizeOffset, i);
          return new Request(sender, connId, path, msg, headerSizeOffset, headerDataOffset,
            headerDataOffset + headerSize + 1, recvJson);
        }
      }
      throw new IllegalArgumentException(
        "Message was not in the format: ID PATH SIZE:HEADERS,SIZE:BODY,");
    }

    public String getSender() { return sender; }
    public String getConnId() { return connId; }
    public String getPath() { return path; }

    /**
     * @return the first header for the specified name, or null if the header
     *   doesn't exsist. Header field names are case-insensitive per rfc 2616,
     *   but case is preserved, so a fast lookup is possible if the case of the
     *   name matches. Otherwise, the header is looked up case-insensitively.
     */
    public String getHeader(String name) {
      final List<String> result = getHeaderValues(name);
      return result == null || result.isEmpty() ? null : result.get(0);
    }

    /**
     * @return immutable List of the header values for the specified name;
     *   if the there is a header for the specified name, always returns a non-null list;
     *   returns null if there is no header for the specified name. Header field
     *   names are case-insensitive per rfc 2616, but case is preserved, so a fast
     *   lookup is possible if the case of the name matches. Otherwise, the header
     *   is looked up case-insensitively.
     */
    public List<String> getHeaderValues(String name) {
      final List<String> result = getHeaders().get(name);
      if (result != null) return result;
      
      // must support case insensitivity of headers per rfc 2616
      for (final Entry<String, List<String>> header : getHeaders().entrySet()) {
        if (header.getKey().equalsIgnoreCase(name)) return header.getValue();
      }
      return null;
    }

    /**
     * @return immutable map of all headers and values; never null. Headers with
     *   multiple values are normalized. If more that one header with a name exists, the
     *   values are combined combined with a ", " as per RFC 2616. Map iteration
     *   preserves the order of the headers.
     */
    public Map<String, String> getCondensedHeaders() {
      final Map<String, List<String>> headers = getHeaders();
      final Map<String, String> result = new LinkedHashMap<String, String>(headers.size());
      for (final Entry<String, List<String>> entry : getHeaders().entrySet()) {
        result.put(entry.getKey(), join(entry.getValue(), ", "));
      }
      return Collections.unmodifiableMap(result);
    }

    /**
     * @return deeply immutable header map; map is never null and map values are never null,
     *   but may be empty. Map iteration preserves the order of the headers. Supports
     *   both TNetstring and JSON Handler protocol
     */
    public Map<String, List<String>> getHeaders() {
      if (cachedHeaders != null) { return cachedHeaders; }
      
      final int headerTypeIndex = bodySizeOffset - 1;
      if (msg[headerTypeIndex] == ',')
        return cachedHeaders = normalizeHeaders(Json.parse(msg, headerDataOffset, headerTypeIndex));
      
      // The mongrel2 http parser only allows headers w/ ascii
      final Map<String, Object> headers = TNetstring.parseWithBytesAsString(msg, headerSizeOffset, ASCII);
      return cachedHeaders = normalizeHeaders(headers);
    }

    public byte[] getBody() { return TNetstring.parse(msg, bodySizeOffset); }

    /**
     * Converts the body bytes to a String using the given charset.
     * Internally optimized to prevent and extra copy.
     */
    public String getBodyAsString(Charset charset) {
      return TNetstring.parseWithBytesAsString(msg, bodySizeOffset, charset);
    }

    /**
     * @return json data if {@link Connection#recvJson()} was used to get the request,
     *   or the "METHOD" header is "JSON"; else the empty Map (never null).
     * @throws IllegalArgumentException if body is not valid JSON
     */
    public Map<String, Object> getData() {
      return recvJson || "JSON".equals(getHeader("METHOD"))
        ? Json.parse(msg, bodySizeOffset, msg.length - 1)
        : Collections.<String, Object>emptyMap();
    }

    public boolean isDisconnect() {
      return "JSON".equals(getHeader("METHOD"))
        && "disconnect".equals(getData().get("type"));
    }

    public boolean shouldClose() {
      return "close".equals(getHeader("connection"))
        || "HTTP/1.0".equals(getHeader("VERSION"));
    }

    @Override public String toString() {
      return "Request [sender=" + sender + ", connId=" + connId + ", path=" + path
        + ", headers=" + getCondensedHeaders() + ", body (dumped as ascii)=" + getBodyAsString(ASCII) +"]";
    }
    @Override public int hashCode() { return hash(msg); }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Request)) return false;
      final Request that = (Request) o;
      return Arrays.equals(msg, that.msg);
    }

    // ensure header values are always a List, which may be a singletonList, or the empty list.
    // All List types are unmodifiable, and so is the resulting Map.
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> normalizeHeaders(Map<String, Object> headers) {
      final Map<String, List<String>> result = new LinkedHashMap<String, List<String>>(headers.size());
      for (final Map.Entry<String, Object> entry : headers.entrySet()) {
        final Object v = entry.getValue();
        if (v instanceof String)
          result.put(entry.getKey(), Collections.singletonList((String) v));
        else if (v instanceof List)
          result.put(entry.getKey(), Collections.unmodifiableList((List<String>) v));
        else if (v == null)
          result.put(entry.getKey(), Collections.<String>emptyList());
        else throw new IllegalStateException("tnetstring or json header values must be string or list of string");
      }
      return Collections.unmodifiableMap(result);
    }
  }

  /**
   * A Connection object manages the connection between your handler and a Mongrel2 server
   * (or servers). It can receive raw requests or JSON encoded requests whether from HTTP
   * or MSG request types, and it can send individual responses or batch responses either
   * raw or as JSON. It also has a way to encode HTTP responses for simplicity since
   * that'll be fairly common.
   */
  public static final class Connection {

    public static final int MAX_IDENTS = 100;

    /** One context per jvm; see: http://zguide.zeromq.org/chapter:all#toc10 */
    private static final Context CTX = ZMQ.context(1);

    private final byte[] senderId;
    private final String subAddress, pubAddress;
    private final Socket reqs, resp;

    /** @throws IllegalArgumentException if any param is null or empty */
    public Connection(String senderId, String subAddress, String pubAddress) {
      this(asciiBytes(senderId), subAddress, pubAddress);
    }

    /**
     * Addresses are 0mq format, for example: tcp://127.0.0.1:9998
     * @throws IllegalArgumentException if any param is null or empty
     */
    private Connection(byte [] senderId, String subAddress, String pubAddress) {
      this.senderId = checkNotNullOrEmpty(senderId, "must specify a senderId");
      this.subAddress = checkNotNullOrEmpty(subAddress, "must specify a subAddress");
      this.pubAddress = checkNotNullOrEmpty(pubAddress, "must specify a pubAddress");
      this.reqs = CTX.socket(ZMQ.PULL);
      reqs.connect(subAddress);
      this.resp = CTX.socket(ZMQ.PUB);
      resp.setIdentity(senderId);
      resp.connect(pubAddress);
    }

    /**
     * @return created mongrel2 Request from 0mq.
     */
    public Request recv() { return Request.parse(reqs.recv(0), false); }

    /**
     * Same as regular recv, but assumes the body is JSON. Normally Request just
     * does this if the METHOD  header is 'JSON' but you can use this to force it
     * for say HTTP requests.
     * @see {@link Request#getData()}
     * @return created mongrel2 Request from 0mq.
     */
    public Request recvJson() { return Request.parse(reqs.recv(0), true); }

    /** Raw send to the given connection ID at the given uuid. */ 
    void send(String uuid, String connId, byte[] msg) {
      final byte[] checkedMsg = msg == null ? EMPTY_BYTE_ARRAY : msg;
      final byte[] header = asciiBytes(uuid + ' ' + connId.length() + ':' + connId + ", ");
      resp.send(concat(header, checkedMsg), 0);
    }

    /** Reply based on the given Request object and message. */
    public void reply(Request req, byte[] msg) { send(req.sender, req.connId, msg); }
    public void reply(Request req, String msg, Charset charset) {
      send(req.sender, req.connId, getBytes(msg, charset));
    }
    /** Same as reply, but tries to convert data to JSON first. */
    public void replyJson(Request req, Map<String, Object> jsonData) {
      send(req.sender, req.connId, Json.dump(jsonData));
    }

    /**
     * Basic HTTP response mechanism which will take your body, any headers you've 
     * made, and encode them so that the browser gets them.
     */
    public void replyHttp(Request req, String body, Charset bodyCharset) {
      replyHttp(req, body, bodyCharset, Collections.<String, String>emptyMap());
    }
    public void replyHttp(Request req, String body, Charset bodyCharset, Map<String, String> headers) {
      replyHttp(req, body, bodyCharset, headers, 200);
    }
    public void replyHttp(Request req, String body, Charset bodyCharset, Map<String, String> headers, int code) {
      replyHttp(req, body, bodyCharset, headers, code, "OK");
    }
    public void replyHttp(Request req, String body, Charset bodyCharset, Map<String, String> headers,
      int code, String status) {
      reply(req, httpResponse(getBytes(body, bodyCharset), bodyCharset, code, status, headers));
    }

    public void replyHttp(Request req, byte[] body) {
      replyHttp(req, body, Collections.<String, String>emptyMap());
    }
    public void replyHttp(Request req, byte[] body, Map<String, String> headers) {
      replyHttp(req, body, headers, 200);
    }
    public void replyHttp(Request req, byte[] body, Map<String, String> headers, int code) {
      replyHttp(req, body, headers, code, "OK");
    }
    public void replyHttp(Request req, byte[] body, Map<String, String> headers, int code, String status) {
      reply(req, httpResponse(body, null, code, status, headers));
    }

    /**
     * This lets you send a single message to many currently connected clients.
     * There's a MAX_IDENTS that you should not exceed, so chunk your targets as needed.
     * Each target will receive the message once by Mongrel2, but you don't have 
     * to loop which cuts down on reply volume.
     */
    public void deliver(String uuid, Iterable<String> idents, byte[] msg) {
      send(uuid, join(idents, " "), msg);
    }
    public void deliver(String uuid, Iterable<String> idents, String msg, Charset charset) {
      send(uuid, join(idents, " "), getBytes(msg, charset));
    }
    /** Same as {@link Connection#deliver(String, Iterable, byte[])}, but converts to JSON first. */
    public void deliverJson(String uuid, Iterable<String> idents, Map<String, Object> jsonData) {
      deliver(uuid, idents, Json.dump(jsonData));
    }

    /**
     * Same as deliver, but builds an HTTP response, which means, yes, you can 
     * reply to multiple connected clients waiting for an HTTP response from one
     * handler. Kinda cool.
     */
    public void deliverHttp(String uuid, Iterable<String> idents, String body,
      Charset bodyCharset) {
      deliverHttp(uuid, idents, body, bodyCharset, Collections.<String, String>emptyMap());
    }
    public void deliverHttp(String uuid, Iterable<String> idents, String body,
      Charset bodyCharset, Map<String, String> headers) {
      deliverHttp(uuid, idents, body, bodyCharset, headers, 200);
    }
    public void deliverHttp(String uuid, Iterable<String> idents, String body,
      Charset bodyCharset, Map<String, String> headers, int code) {
      deliverHttp(uuid, idents, body, bodyCharset, headers,code, "OK");
    }
    public void deliverHttp(String uuid, Iterable<String> idents, String body,
      Charset bodyCharset, Map<String, String> headers, int code, String status) {
      final byte[] checkedBody = body == null ? EMPTY_BYTE_ARRAY : getBytes(body, bodyCharset);
      deliver(uuid, idents, httpResponse(checkedBody, bodyCharset, code, status, headers));
    }

    public void deliverHttp(String uuid, Iterable<String> idents, byte[] body) {
      deliverHttp(uuid, idents, body, Collections.<String, String>emptyMap());
    }
    public void deliverHttp(String uuid, Iterable<String> idents, byte[] body,
      Map<String, String> headers) {
      deliverHttp(uuid, idents, body, headers, 200);
    }
    public void deliverHttp(String uuid, Iterable<String> idents, byte[] body,
      Map<String, String> headers, int code) {
      deliverHttp(uuid, idents, body, headers, code, "OK");
    }
    public void deliverHttp(String uuid, Iterable<String> idents, byte[] body,
      Map<String, String> headers, int code, String status) {
      deliver(uuid, idents, httpResponse(body, null, code, status, headers));
    }

    /** Tells mongrel2 to explicitly close the HTTP connection. */
    public void close(Request req) { reply(req, EMPTY_BYTE_ARRAY); }

    /** Same as close but does it to a whole bunch of idents at a time. */
    public void deliverClose(String uuid, Iterable<String> idents) {
      deliver(uuid, idents, EMPTY_BYTE_ARRAY);
    }
    
    public byte[] getSenderId() {
      final byte[] copy = new byte[senderId.length];
      System.arraycopy(senderId, 0, copy, 0, senderId.length);
      return copy;
    }
    public String getSenderIdString() { return asciiFromRange(senderId, 0, senderId.length); }
    public String getSubAddress() { return subAddress; }
    public String getPubAddress() { return pubAddress; }

    @Override public String toString() {
      return "Connection [senderId=" + Arrays.toString(senderId) + ", subAddress="
        + subAddress + ", pubAddress=" + pubAddress + "]";
    }
    @Override public int hashCode() {
      return hash(senderId, pubAddress, subAddress);
    }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Connection)) return false;
      final Connection that = (Connection) o;
      return Arrays.equals(senderId, that.senderId) && eq(pubAddress, that.pubAddress)
        && eq(subAddress, that.subAddress);
    }

    private static byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * Implementation concatenates bytes arrays to prevent unnecessary copies and encoding.
     * Iteration order of the header Map is preserved.
     * 
     * The ;charset=<charset> is appended to Content-Type if the charset is supplied
     * and not already present in the header
     */
    private static byte[] httpResponse(byte[] body, Charset possibleCharset,
      int code, String status, Map<String, String> headers) {
      final byte[] bodyBytes = body == null ? EMPTY_BYTE_ARRAY : body;
      final Map<String, String> headersCopy = new LinkedHashMap<String, String>(headers);
      headersCopy.put("Content-Length", String.valueOf(bodyBytes.length));
      if (possibleCharset != null) {
        final String contentType = headersCopy.get("Content-Type");
        if (contentType != null && !contentType.contains("charset"))
          headersCopy.put("Content-Type", contentType + ';' + possibleCharset.name());
      }
      final StringBuilder head = new StringBuilder("HTTP/1.1 ").append(code)
        .append(' ').append(status).append("\r\n");
      for (final Entry<String, String> header : headersCopy.entrySet())
        head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
      head.append("\r\n");
      // mongrel2 only allows ASCII headers
      return concat(asciiBytes(head), bodyBytes);
    }
  }

  private static String asciiFromRange(byte[] msg, int from, int to) {
    final int size = to - from;
    final char[] result = new char[size];
    for (int i = 0; i < size; i++) {
      final byte b = msg[from + i];
      result[i] = b < 0 ? '?' : (char) b;
    }
    return String.valueOf(result);
  }

  private static final byte REPLACEMENT = '?';

  private static byte[] asciiBytes(CharSequence s) {
    final int size = s.length();
    final byte[] result = new byte[size];
    for (int i = 0; i < size; i++) {
      final char c = s.charAt(i);
      result[i] = c > 127 ? REPLACEMENT : (byte) c;
    }
    return result;
  }

  private static byte[] getBytes(String s, Charset charset) {
    if(!ASCII.equals(charset)) {
      try {
        return s.getBytes(charset.name());
      } catch (final UnsupportedEncodingException e) {
        throw new IllegalArgumentException(e);
      }
    }
    return asciiBytes(s);
  }

  private static byte[] concat(byte[]... arrays) {
    int length = 0;
    for (final byte[] array : arrays) length += array.length;
    final byte[] result = new byte[length];
    int pos = 0;
    for (final byte[] array : arrays) {
      System.arraycopy(array, 0, result, pos, array.length);
      pos += array.length;
    }
    return result;
  }

  private static String join(Iterable<?> c, String sep) {
    Iterator<?> i;
    if (c == null || (!(i = c.iterator()).hasNext())) return "";
    final StringBuilder result = new StringBuilder(String.valueOf(i.next()));
    while (i.hasNext()) result.append(sep).append(i.next());
    return result.toString();
  }

  private static <T> T checkNotNull(T ref, String errorMessage) {
    if (ref == null) throw new NullPointerException(errorMessage);
    return ref;
  }

  private static String checkNotNullOrEmpty(String ref, String errorMessage) {
    checkNotNull(ref, errorMessage);
    if (ref.isEmpty()) throw new IllegalArgumentException(errorMessage);
    return ref;
  }

  private static byte[] checkNotNullOrEmpty(byte[] ref, String errorMessage) {
    checkNotNull(ref, errorMessage);
    if (ref.length == 0) throw new IllegalArgumentException(errorMessage);
    return ref;
  }

  private static int hash(Object... objects) { return Arrays.deepHashCode(objects); }
  private static boolean eq(Object a, Object b) { return a == b || (a != null && a.equals(b)); }

  /* classes are lazy loaded so jackson is not required if json is not used */
  static final class Json {
    private Json() { }
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    static byte[] dump(Map<String, Object> jsonData) {
      try {
        return JSON_MAPPER.writeValueAsBytes(jsonData);
      } catch (final JsonGenerationException e) {
        throw new IllegalStateException(e.getMessage(), e);
      } catch (final JsonMappingException e) {
        throw new IllegalStateException(e.getMessage(), e);
      } catch (final IOException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    }

    static Map<String, Object> parse(byte[] msg, int from, int to) {
      final int length = to - from;
      try {
        return JSON_MAPPER.readValue(msg, from, length,
          TypeFactory.mapType(Map.class, String.class, Object.class));
      } catch (final JsonParseException e) {
        throw new IllegalStateException(e.getMessage(), e);
      } catch (final JsonMappingException e) {
        throw new IllegalStateException(e.getMessage(), e);
      } catch (final IOException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    }
  }

  /**
   * A very fast tnetstring parser and dumper (serializers/deserializer). Parsing
   * produces no side affect garbage for all core tnetstring types (except for tnetstring
   * floating poing numbers). Each data element is parsed directly from the tnetstring
   * byte array range without converstion to intermediate String or temporary object holders.<br><br>
   * 
   * Supports the full tnetstrings spec as of 2011/3/16 (blobs, dicts, lists, integers,
   * floats, boolean and null).<br><br>
   * 
   * Maps preserve order (implementation is backed by LinkedHashMap). Tnetstring
   * blobs are returned as java byte[] arrays. Note that byte array keys in a Map are almost
   * useless in java--you can't really get a value by the byte[] array key because a byte array
   * uses the default identity based equality and hash from Object. You most likely
   * want the keys as Strings if you're getting data by keys. Otherwise, treat the map as a List
   * of pairs blobs in them--simply iterate over the map entries.<br><br>
   * 
   * Use the convenience methods {@link #parseWithBytesAsString(byte[], Charset)}
   * to parse the object graph with every occurance of byte[] converted to a String using
   * the specified charset. If you are expecting an object graph with both String and byte[]
   * data, you'll need to use {@link #parse(byte[])} and convert the the specific byte arrays
   * you want as Strings yourself.<br><br>
   * 
   * The convenience methods to convert byte[] to Strings are also optimized to prevent
   * the double copy that would occur if you first got the bytes then converted them to Strings.
   * 
   * @author Armando Singer (armando.singer at gmail dot com)
   */
  private static final class TNetstring {

    private TNetstring() { }

    private static final Charset ASCII = Charset.forName("US-ASCII");

    /**
     * @return byte[] or Long or Double or Boolean or Map<byte[], Object> or List<Object> or null;
     *   Map values or List elements may be any of the previously listed types.
     *   Maps preserve order.
     */
    @SuppressWarnings("unchecked")
    public static <T> T parse(final byte[] msg, int offset) {
      return (T) parse(msg, offset, null);
    }

    /**
     * Convenience method to parse with any occurance of byte[] as a Java String
     * and optimized to prevent double copy. String conversion is applied recursively to Map
     * values and list elements if they are byte[] types.
     * 
     * @return String or Long or Double or Boolean or Map<String, Object> or List<Object> or null;
     *   Map values or List elements may be any of the previously listed types.
     *   Maps preserve order.
     */
    @SuppressWarnings("unchecked")
    public static <T> T parseWithBytesAsString(final byte[] msg, int offset, final Charset charset) {
      return (T) parse(msg, offset, charset);
    }

    /** Internal parsing impl w/ an optimization if we want a String that prevents double copy */
    private static Object parse(final byte[] msg, final int offset, final Charset charset) {
      if (msg == null || msg.length < 3)
        throw new IllegalArgumentException("Nestring can't be null or < 3 length");
      final int i = dataIndex(msg, offset);
      final int size = parseSize(msg, offset, i - 1);
      if (size > msg.length)
        throw new IllegalArgumentException("Invalid tnetstring size. Can't be > msg size");
      final int typeIndex = i + size;
      switch (msg[typeIndex]) {
        case ',': return charset == null ? copyRange(msg, i, size) : parseString(msg, i, size, charset);
        case '}': return parseDict(msg, i, size, charset);
        case ']': return parseList(msg, i, size, charset);
        case '#': return parseLong(msg, i, typeIndex);
        case '^': return parseDouble(msg, i, typeIndex);
        case '!': return msg[i] == 't' && msg[i + 1] == 'r' && msg[i + 2] == 'u' && msg[i + 3] == 'e';
        case '~': if (size != 0) throw new IllegalArgumentException("Payload must be 0 length for null.");
          return null;
        default: throw new IllegalArgumentException(
          "Invalid payload type: " + msg[typeIndex] + " at index: " + typeIndex);
      }
    }

    /**
     * @return the parsed SIZE portion of a tnetstring SIZE:DATA,
     *   The integer is parsed directly from the bytes from the specified index,
     *   inclusive, to the specifed index, exclusive. Produces no garbage.
     * @throws NumberFormatException if the size bytes are not an ascii encoded
     *   integer that has no more than 9 digits
     */
    static int parseSize(final byte[] msg, final int from, final int to) {
      final int length = to - from;
      if (length <= 0) throw new IllegalArgumentException(from + " >= " + to);
      if (msg == null) throw new NumberFormatException("null");
      if (length > 9) throw new NumberFormatException("tnetstring size digits can't be > 9");

      int result = 0;
      for (int i = from; i < to; i++) {
        final byte digit = digitFrom(msg[i], msg, from, to);
        result *= 10;
        result += digit;
      }
      return result;
    }

    private static Map<Object, Object> parseDict(final byte[] msg, final int dataIndex,
      final int size, final Charset charset) {
      if (size == 0) return Collections.emptyMap();
      final Map<Object, Object> map = new LinkedHashMap<Object, Object>();
      final int limit = dataIndex + size;
      for (int keyIndex = dataIndex; keyIndex < limit; ) {
        final int keyDataIndex = dataIndex(msg, keyIndex);
        final int keySize = parseSize(msg, keyIndex, keyDataIndex - 1);
        final int valueIndex = keyDataIndex + keySize + 1;
        map.put(parse(msg, keyIndex, charset), parse(msg, valueIndex, charset));
        final int valueDataIndex = dataIndex(msg, valueIndex);
        final int valueSize = parseSize(msg, valueIndex, valueDataIndex - 1);
        keyIndex = valueDataIndex + valueSize + 1;
      }
      return Collections.unmodifiableMap(map);
    }

    private static List<Object> parseList(final byte[] msg, final int dataIndex,
      final int size, final Charset charset) {
      if (size == 0) return Collections.emptyList();
      final List<Object> list = new ArrayList<Object>();
      final int limit = dataIndex + size;
      for (int elementIndex = dataIndex; elementIndex < limit; ) {
        list.add(parse(msg, elementIndex, charset));
        final int elementSize = parseSize(msg, elementIndex, dataIndex(msg, elementIndex) - 1);
        elementIndex = dataIndex(msg, elementIndex) + elementSize + 1;
      }
      return Collections.unmodifiableList(list);
    }

    private static final int dataIndex(final byte[] msg, final int offset) {
      for (int i = offset; i < msg.length; i++)
        if (msg[i] == ':') return i + 1;
      throw new IllegalArgumentException("TNetstring does not have a ':' between offset "
        + offset + " and length " + msg.length);
    }

    private static byte[] copyRange(final byte[] msg, final int offset, final int size) {
      final byte[] copy = new byte[size];
      System.arraycopy(msg, offset, copy, 0, Math.min(msg.length - offset, size));
      return copy;
    }

    private static final long LONG_MULTMIN = Long.MIN_VALUE / 10;
    private static final long LONG_NEG_MULTMAX = -Long.MAX_VALUE / 10;

    /** Parse a long from a byte range. Produces no garbage. */
    static long parseLong(final byte[] msg, final int from, final int to) {
      if (msg == null) throw new NumberFormatException("null");

      final long limit;
      final boolean negative;
      int i = from;
      if (msg[i] == '-') {
        negative = true;
        limit = Long.MIN_VALUE;
        i++;
      } else {
        negative = false;
        limit = -Long.MAX_VALUE;
      }
      byte digit;
      long result = 0;
      if (i < to) {
        digit = digitFrom(msg[i++], msg, from, to);
        result = -digit;
      }
      final long multmin = negative ? LONG_MULTMIN : LONG_NEG_MULTMAX;
      while (i < to) {
        digit = digitFrom(msg[i++], msg, from, to);
        if (result < multmin) throw badNumberFormat(msg, from, to);
        result *= 10;
        if (result < limit + digit) throw badNumberFormat(msg, from, to);
        result -= digit;
      }

      if (negative) {
        if (i > 1) return result;
        throw badNumberFormat(msg, from, to);
      }
      return -result;
    }

    static double parseDouble(final byte[] msg, final int from, final int to) {
      return Double.parseDouble(parseAscii(msg, from, to - from));
    }

    static byte digitFrom(byte ascii, byte[] msg, int from, int to) {
      switch (ascii) {
        case '1': return 1;
        case '2': return 2;
        case '3': return 3;
        case '4': return 4;
        case '5': return 5;
        case '6': return 6;
        case '7': return 7;
        case '8': return 8;
        case '9': return 9;
        case '0': return 0;
        default: throw badNumberFormat(msg, from, to);
      }
    }

    private static NumberFormatException badNumberFormat(byte[] asciiNum, int from, int to) {
      return new NumberFormatException("For input: '" + parseString(asciiNum, from, to - from, ASCII) + '\'');
    }

    /** Parse String with fast & minimum possible garbage path for ASCII */
    static String parseString(final byte[] msg, final int from, final int size, final Charset charset) {
      if(!ASCII.equals(charset)) {
        try {
          return new String(msg, from, size, charset.name());
        } catch (final UnsupportedEncodingException e) {
          throw new IllegalArgumentException(e);
        }
      }
      return parseAscii(msg, from, size);
    }

    private static String parseAscii(final byte[] msg, final int from, final int size) {
      // ascii fast path. ~3x faster than both overloads of new String(msg, from, size, US_ASCII);
      final char[] result = new char[size];
      for (int i = 0; i < size; i++) {
        final byte b = msg[from + i];
        result[i] = b < 0 ? '?' : (char) b;
      }
      return String.valueOf(result);
    }
  }

}
```
