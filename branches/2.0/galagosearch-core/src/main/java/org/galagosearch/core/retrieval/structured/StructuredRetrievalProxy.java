/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.galagosearch.core.retrieval.structured;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import org.galagosearch.tupleflow.Parameters;

/**
 * This acts as the client-side stub for forwarding requests across 
 * a network connection to a remote index.
 *
 * @author irmarc
 */
public class StructuredRetrievalProxy implements InvocationHandler {

  String indexUrl;
  HashSet<String> unImplemented;

  public StructuredRetrievalProxy(String url, Parameters parameters) throws IOException {
    this.indexUrl = url;
    unImplemented = new HashSet<String>();
    unImplemented.add("runAsynchronousQuery");
    unImplemented.add("waitForAsynchronousQuery");
  }

  public void close() throws IOException {
    // Nothing to do - index is serving remotely - possibly to several handlers
  }

  public Object invoke(Object caller, Method method, Object[] args) throws Throwable {

    // Check to make sure we shouldn't skip it
    if (unImplemented.contains(method.getName())) {
      throw new UnsupportedOperationException("Proxy class does not support this operation.");
    }

    StringBuilder request = new StringBuilder(indexUrl);
    request.append("/stream");

    URL resource = new URL(request.toString());
    HttpURLConnection connection = (HttpURLConnection) resource.openConnection();
    connection.setRequestMethod("GET");

    // Write data directly to the stream
    OutputStream writeStream = connection.getOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(writeStream);

    // First the Method, which is not serializable directly
    oos.writeUTF(method.getName());

    // Write length of arguments
    oos.writeShort((short) args.length);

    // Types of arguments
    for (int i = 0; i < args.length; i++) {
      oos.writeObject(args[i].getClass());
    }

    // Now write them out via serialization
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      oos.writeObject(arg);
    }

    // Wait for response
    InputStream stream = connection.getInputStream();

    // Now get the response and re-instantiate
    // This requires that the return type is serializable
    ObjectInputStream ois = new ObjectInputStream(stream);
    Object response = ois.readObject();

    // Do we want to keep reconnecting and disconnecting?
    // Maybe a persistent connection is worth it?
    connection.disconnect();
    return response;
  }
}
