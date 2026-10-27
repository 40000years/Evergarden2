package com.example.voidscape.pack;

import com.example.advancemagic.pack.delivery.ServerPortPackHost;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.Plugin;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

/** Serves only the bundled ZIP, never a filesystem directory. */
public final class PackHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService workers;
    private final ServerPortPackHost shared;
    private final byte[] pack;
    private final String path, etag;
    private final ConcurrentMap<String,byte[]> additions=new ConcurrentHashMap<>();

    public PackHttpServer(String bind, int port, byte[] pack, String sha1) throws IOException {
        this.pack=pack.clone();this.path="/evergarden/"+sha1+".zip";this.etag="\""+sha1+"\"";shared=null;
        server=HttpServer.create(new InetSocketAddress(bind,port),32);
        workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{
            Thread t=new Thread(r,"evergarden-pack");t.setDaemon(true);return t;
        },new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(workers);server.createContext("/",this::serve);server.start();
    }
    /** Answers on the Minecraft port itself; fails when the server offers no hook for it. */
    public PackHttpServer(Plugin plugin, byte[] pack, String sha1) throws IOException {
        this.pack=pack.clone();this.path="/evergarden/"+sha1+".zip";this.etag="\""+sha1+"\"";server=null;workers=null;
        shared=ServerPortPackHost.install(plugin,requested->requested.equals(path)?this.pack:additions.get(requested));
    }
    private void serve(HttpExchange exchange) throws IOException {
        try(exchange) {
            String requested=exchange.getRequestURI().getRawPath();
            byte[] content=requested.equals(path)?pack:additions.get(requested);
            if(content==null) {exchange.sendResponseHeaders(404,-1);return;}
            String contentEtag=requested.equals(path)?etag:"\""+requested.substring(requested.lastIndexOf('/')+1,requested.length()-4)+"\"";
            String method=exchange.getRequestMethod();
            if(!method.equals("GET")&&!method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow","GET, HEAD");exchange.sendResponseHeaders(405,-1);return;
            }
            var headers=exchange.getResponseHeaders();
            headers.set("Content-Type","application/zip");headers.set("X-Content-Type-Options","nosniff");
            headers.set("Cache-Control","public, max-age=31536000, immutable");headers.set("ETag",contentEtag);
            if(contentEtag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {exchange.sendResponseHeaders(304,-1);return;}
            headers.set("Content-Length",Integer.toString(content.length));
            exchange.sendResponseHeaders(200,method.equals("HEAD")?-1:content.length);
            if(method.equals("GET"))exchange.getResponseBody().write(content);
        }
    }
    public String path(){return path;}
    /** Existing hashes stay available while clients finish earlier downloads. */
    public String addPack(byte[] bytes,String sha1){
        if(!sha1.matches("[a-f0-9]{40}"))throw new IllegalArgumentException("Invalid SHA-1");
        String next="/evergarden/"+sha1+".zip";additions.putIfAbsent(next,bytes.clone());return next;
    }
    public boolean sharesServerPort(){return shared!=null;}
    /** -1 while sharing the Minecraft port. */
    public int port(){return server==null?-1:server.getAddress().getPort();}
    @Override public void close(){if(shared!=null)shared.close();else{server.stop(0);workers.shutdownNow();}}
}
