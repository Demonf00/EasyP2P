package com.esay.relay;

import java.net.InetSocketAddress;

public class Main {
  public static void main(String[] args) throws Exception {
    String host = System.getProperty("host", "0.0.0.0");
    int port = Integer.getInteger("port", 8989);
    RelayServer server = new RelayServer(new InetSocketAddress(host, port), "/relay");
    server.start();
    System.out.println("[relay] listening on " + host + ":" + port + " path=/relay");
  }
}
