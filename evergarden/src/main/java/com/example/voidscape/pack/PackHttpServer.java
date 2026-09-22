package com.example.voidscape.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

/** Serves only the bundled ZIP, never a filesystem directory. */
public final class PackHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService workers;
    private final byte[] pack;
    private final String path, etag;

    public PackHttpServer(String bind, int port, byte[] pack, String sha1) throws IOException {
        this.pack=pack.clone();this.path="/evergarden/"+sha1+".zip";this.etag="\""+sha1+"\"";
        server=HttpServer.create(new InetSocketAddress(bind,port),32);
        workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{
            Thread t=new Thread(r,"evergarden-pack");t.setDaemon(true);return t;
        },new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(workers);server.createContext("/",this::serve);server.start();
    }
    private void serve(HttpExchange exchange) throws IOException {
        try(exchange) {
            if(!exchange.getRequestURI().getRawPath().equals(path)) {exchange.sendResponseHeaders(404,-1);return;}
            String method=exchange.getRequestMethod();
            if(!method.equals("GET")&&!method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow","GET, HEAD");exchange.sendResponseHeaders(405,-1);return;
            }
            var headers=exchange.getResponseHeaders();
            headers.set("Content-Type","application/zip");headers.set("X-Content-Type-Options","nosniff");
            headers.set("Cache-Control","public, max-age=31536000, immutable");headers.set("ETag",etag);
            if(etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {exchange.sendResponseHeaders(304,-1);return;}
            headers.set("Content-Length",Integer.toString(pack.length));
            exchange.sendResponseHeaders(200,method.equals("HEAD")?-1:pack.length);
            if(method.equals("GET"))exchange.getResponseBody().write(pack);
        }
    }
    public String path(){return path;}
    public int port(){return server.getAddress().getPort();}
    @Override public void close(){server.stop(0);workers.shutdownNow();}
}
