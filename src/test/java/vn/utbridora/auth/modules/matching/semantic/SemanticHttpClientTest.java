package vn.utbridora.auth.modules.matching.semantic;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticFixtures.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticProtocol.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Real loopback HTTP protocol, synthetic server responses; not an E5 model test. */
class SemanticHttpClientTest {
    private HttpServer server;private ExecutorService executor;private SemanticHttpClient client;
    private final List<Passage> passages=List.of(new Passage("0","JAVA"),new Passage("1","SQL"));

    private void start(Function<Request,Object> response,int status,long delayBodyMillis) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"synthetic-ai-protocol-test");t.setDaemon(true);return t;});
        server.setExecutor(executor);
        server.createContext("/internal/v1/similarity",exchange->{
            try {
                assertEquals(TOKEN,exchange.getRequestHeaders().getFirst("X-AI-Token"));
                byte[] request=exchange.getRequestBody().readAllBytes();
                String text=new String(request,StandardCharsets.UTF_8);
                assertFalse(text.contains(id(2)));assertFalse(text.contains(id(2001)));
                Request value=JSON.readValue(request,Request.class);
                byte[] data=JSON.writeValueAsBytes(response.apply(value));
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,data.length);
                if(delayBodyMillis>0) {
                    exchange.getResponseBody().write(data,0,1);exchange.getResponseBody().flush();
                    try {Thread.sleep(delayBodyMillis);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                    exchange.getResponseBody().write(data,1,data.length-1);
                } else exchange.getResponseBody().write(data);
            } finally {exchange.close();}
        });
        server.start();client=new SemanticHttpClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),TOKEN,REVISION,Duration.ofSeconds(2));
    }
    private Map<String,Object> valid(Request request) {
        var result=new LinkedHashMap<String,Object>();
        result.put("schemaVersion",SCHEMA);result.put("requestId",request.requestId());result.put("templateVersion",TEMPLATE);
        result.put("modelId",MODEL);result.put("modelRevision",REVISION);result.put("dimension",384);result.put("aggregationVersion",AGGREGATION);
        var pairs=new ArrayList<Map<String,Object>>();
        for(var p:request.passages()) {
            var pair=new HashMap<String,Object>();pair.put("key",p.key());pair.put("status","READY");pair.put("score",0.81);
            pair.put("queryChunks",1);pair.put("passageChunks",1);pair.put("limited",false);pairs.add(pair);
        }
        result.put("results",pairs);return result;
    }
    @AfterEach void stop() {if(client!=null) client.close();if(server!=null) server.stop(0);if(executor!=null) executor.shutdownNow();}

    @Test void validResponseIsCorrelatedAndMappedBackToExpectedOrder() throws Exception {
        start(request->{var value=valid(request);Collections.reverse((List<?>)value.get("results"));return value;},200,0);
        var result=client.compare("Required JAVA",passages,Duration.ofSeconds(2));
        assertEquals(List.of("0","1"),result.stream().map(Pair::key).toList());assertEquals(0.81,result.getFirst().score());
    }
    @Test void enrichedTemplateEchoAndEmojiUseCodePointBounds() throws Exception {
        start(request->{assertEquals(ADVISORY_TEMPLATE,request.templateVersion());var value=valid(request);value.put("templateVersion",request.templateVersion());return value;},200,0);
        client.close();client=new SemanticHttpClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),TOKEN,REVISION,Duration.ofSeconds(2),ADVISORY_TEMPLATE);
        assertEquals(2,client.compare("😀".repeat(8192),passages,Duration.ofSeconds(2)).size());
    }
    @Test void advisoryRejectsCanonicalLabelOnResponse() throws Exception {
        start(this::valid,200,0);client.close();client=new SemanticHttpClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),TOKEN,REVISION,Duration.ofSeconds(2),ADVISORY_TEMPLATE);
        assertEquals("AI_INVALID_RESPONSE",assertThrows(Unavailable.class,()->client.compare("JD",passages,Duration.ofSeconds(2))).getMessage());
    }
    @ParameterizedTest @ValueSource(strings={"schema","request","template","model","revision","dimension","aggregation","extra","duplicate","unknownKey","score","nullScore","missingLimited","zeroChunks","numericCoercion"})
    @SuppressWarnings("unchecked")
    void rejectsUntrustedResponseInsteadOfAcceptingHttp200(String mutation) throws Exception {
        start(request->{var value=valid(request);var pairs=(List<Map<String,Object>>)value.get("results");
            switch(mutation) {
                case "schema" -> value.put("schemaVersion","other");
                case "request" -> value.put("requestId",id(99));
                case "template" -> value.put("templateVersion","other");
                case "model" -> value.put("modelId","other");
                case "revision" -> value.put("modelRevision","a".repeat(40));
                case "dimension" -> value.put("dimension",385);
                case "aggregation" -> value.put("aggregationVersion","other");
                case "extra" -> value.put("privateInput","PRIVATE_MUST_NOT_APPEAR_IN_EXCEPTION");
                case "duplicate" -> pairs.get(1).put("key","0");
                case "unknownKey" -> pairs.get(1).put("key","999");
                case "score" -> pairs.get(0).put("score",1.5);
                case "nullScore" -> pairs.get(0).put("score",null);
                case "missingLimited" -> pairs.get(0).remove("limited");
                case "zeroChunks" -> pairs.get(0).put("queryChunks",0);
                case "numericCoercion" -> pairs.get(0).put("score","0.81");
            }
            return value;
        },200,0);
        var error=assertThrows(Unavailable.class,()->client.compare("JAVA",passages,Duration.ofSeconds(2)));
        assertEquals("AI_INVALID_RESPONSE",error.getMessage());assertFalse(error.toString().contains("PRIVATE"));
    }
    @Test void upstreamFailureDoesNotEchoItsBody() throws Exception {
        start(request->Map.of("error","PRIVATE_UPSTREAM_EXCEPTION"),503,0);
        assertEquals("AI_UNAVAILABLE",assertThrows(Unavailable.class,()->client.compare("JAVA",passages,Duration.ofSeconds(2))).getMessage());
    }
    @Test void responseBodyIsByteBounded() throws Exception {
        start(request->Map.of("tooLarge","x".repeat(140000)),200,0);
        assertThrows(Unavailable.class,()->client.compare("JAVA",passages,Duration.ofSeconds(2)));
    }
    @Test void deadlineIncludesSlowResponseBody() throws Exception {
        start(this::valid,200,800);
        long start=System.nanoTime();
        assertEquals("AI_TIMEOUT",assertThrows(Unavailable.class,()->client.compare("JAVA",passages,Duration.ofMillis(150))).getMessage());
        assertTrue(Duration.ofNanos(System.nanoTime()-start).compareTo(Duration.ofMillis(700))<0);
    }
    @Test void requestIsBoundedBeforeSending() {
        client=new SemanticHttpClient(URI.create("http://127.0.0.1:9"),TOKEN,REVISION,Duration.ofSeconds(2));
        assertEquals("AI_INVALID_INPUT",assertThrows(Unavailable.class,()->client.compare("x".repeat(8193),passages,Duration.ofSeconds(2))).getMessage());
        assertThrows(Unavailable.class,()->client.compare("JAVA",Collections.nCopies(17,new Passage("0","JAVA")),Duration.ofSeconds(2)));
    }
    @ParameterizedTest @ValueSource(strings={"file:///tmp/model","http://user:PRIVATE@127.0.0.1:9","http://127.0.0.1/path","http://127.0.0.1?key=PRIVATE","https://127.0.0.1/#PRIVATE"})
    void noUserInfoQueryFragmentOrNonInternalPath(String base) {
        var error=assertThrows(IllegalArgumentException.class,()->new SemanticHttpClient(URI.create(base),TOKEN,REVISION,Duration.ofSeconds(2)));
        assertFalse(error.toString().contains("PRIVATE"));
    }
}
