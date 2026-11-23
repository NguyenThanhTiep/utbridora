package vn.utbridora.auth.modules.matching.semantic;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static vn.utbridora.auth.modules.matching.semantic.SemanticProtocol.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** No redirects, bounded bytes and an overall deadline including response-body streaming. */
public final class SemanticHttpClient implements Client,AutoCloseable {
    private static final int MAX_REQUEST=262144,MAX_RESPONSE=131072;
    private final HttpClient http;
    private final URI endpoint;
    private final String token,revision;
    private final Duration timeout;
    private final String template;

    static URI baseUri(String value) {
        try {return URI.create(value);}
        catch(RuntimeException invalid) {throw new IllegalArgumentException("Invalid internal AI base URL");}
    }

    SemanticHttpClient(URI base,String token,String revision,Duration timeout) {
        this(base,token,revision,timeout,TEMPLATE);
    }
    public SemanticHttpClient(URI base,String token,String revision,Duration timeout,String template) {
        if(!Set.of(TEMPLATE,ADVISORY_TEMPLATE).contains(template)) throw new IllegalArgumentException("Invalid semantic template");
        this.template=template;
        if(base.getHost()==null || !Set.of("http","https").contains(base.getScheme()) || base.getUserInfo()!=null
            || base.getQuery()!=null || base.getFragment()!=null || !(base.getPath().isEmpty() || base.getPath().equals("/")))
            throw new IllegalArgumentException("Invalid internal AI base URL");
        if(token==null || !token.matches("[A-Za-z0-9_-]{32,256}") || revision==null || !revision.matches("[a-f0-9]{40}"))
            throw new IllegalArgumentException("Invalid internal AI credentials/model revision");
        if(timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(60))>0)
            throw new IllegalArgumentException("Invalid AI deadline");
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1).build();
        this.endpoint=base.resolve("/internal/v1/similarity");this.token=token;this.revision=revision;this.timeout=timeout;
    }

    public List<Pair> compare(String query,List<Passage> passages,Duration remaining) {
        if(!validText(query) || passages==null || passages.isEmpty() || passages.size()>MAX_BATCH
            || passages.stream().anyMatch(p->p==null || p.key()==null || !p.key().matches("[0-9]{1,6}") || !validText(p.text()))
            || passages.stream().map(Passage::key).distinct().count()!=passages.size()) throw new Unavailable("AI_INVALID_INPUT");
        Duration deadline=remaining.compareTo(timeout)<0?remaining:timeout;
        if(deadline.isNegative() || deadline.isZero()) throw new Unavailable("AI_TIMEOUT");
        String requestId=UUID.randomUUID().toString();
        byte[] data=JSON.writeValueAsBytes(new Request(SCHEMA,requestId,template,query,List.copyOf(passages)));
        if(data.length>MAX_REQUEST) throw new Unavailable("AI_INVALID_INPUT");
        var request=HttpRequest.newBuilder(endpoint).timeout(deadline).header("Content-Type","application/json")
            .header("X-AI-Token",token).POST(HttpRequest.BodyPublishers.ofByteArray(data)).build();
        var pending=http.sendAsync(request,info->new BoundedSubscriber(MAX_RESPONSE));
        try {
            var response=pending.get(Math.max(1,deadline.toMillis()),TimeUnit.MILLISECONDS);
            if(response.statusCode()!=200) throw new Unavailable(Set.of(400,401,403,413,415,422).contains(response.statusCode())?"AI_INVALID_RESPONSE":"AI_UNAVAILABLE");
            String contentType=response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].strip();
            if(!contentType.equalsIgnoreCase("application/json")) throw new Unavailable("AI_INVALID_RESPONSE");
            Response value;
            try { value=JSON.readValue(response.body(),Response.class); }
            catch(RuntimeException invalid) { throw new Unavailable("AI_INVALID_RESPONSE"); }
            return validate(value,requestId,passages);
        } catch(TimeoutException timeout) {
            pending.cancel(true);throw new Unavailable("AI_TIMEOUT");
        } catch(InterruptedException interrupted) {
            pending.cancel(true);Thread.currentThread().interrupt();throw new Unavailable("AI_UNAVAILABLE");
        } catch(ExecutionException failed) {
            throw new Unavailable("AI_UNAVAILABLE");
        }
    }
    private List<Pair> validate(Response value,String requestId,List<Passage> expected) {
        if(value==null || !SCHEMA.equals(value.schemaVersion()) || !requestId.equals(value.requestId())
            || !template.equals(value.templateVersion()) || !MODEL.equals(value.modelId()) || !revision.equals(value.modelRevision())
            || !Integer.valueOf(DIMENSION).equals(value.dimension()) || !AGGREGATION.equals(value.aggregationVersion())
            || value.results()==null || value.results().size()!=expected.size()) throw new Unavailable("AI_INVALID_RESPONSE");
        var byKey=new HashMap<String,Pair>();
        for(var pair:value.results()) {
            if(pair==null || pair.key()==null || byKey.put(pair.key(),pair)!=null || pair.limited()==null
                || pair.queryChunks()==null || pair.passageChunks()==null || pair.queryChunks()<0 || pair.queryChunks()>4
                || pair.passageChunks()<0 || pair.passageChunks()>4) throw new Unavailable("AI_INVALID_RESPONSE");
            if("READY".equals(pair.status())) {
                if(pair.score()==null || !Double.isFinite(pair.score()) || pair.score() < -1 || pair.score() > 1
                    || pair.queryChunks()==0 || pair.passageChunks()==0) throw new Unavailable("AI_INVALID_RESPONSE");
            } else if(!"NO_DATA".equals(pair.status()) || pair.score()!=null || pair.queryChunks()!=0 && pair.passageChunks()!=0)
                throw new Unavailable("AI_INVALID_RESPONSE");
        }
        if(!byKey.keySet().equals(new HashSet<>(expected.stream().map(Passage::key).toList()))) throw new Unavailable("AI_INVALID_RESPONSE");
        return expected.stream().map(p->byKey.get(p.key())).toList();
    }
    @Override public void close() { http.shutdownNow(); }
    private boolean validText(String s) {
        if(s==null) return false;
        if(TEMPLATE.equals(template)) return s.length()<=MAX_TEXT;
        if(s.codePointCount(0,s.length())>MAX_TEXT || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768) return false;
        for(int i=0;i<s.length();i++) if(Character.isSurrogate(s.charAt(i))) {
            if(!Character.isHighSurrogate(s.charAt(i)) || i+1>=s.length() || !Character.isLowSurrogate(s.charAt(++i))) return false;
        }
        return true;
    }

    private static final class BoundedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
        BoundedSubscriber(int limit) {this.limit=limit;}
        public CompletionStage<byte[]> getBody() {return result;}
        public void onSubscribe(Flow.Subscription subscription) {this.subscription=subscription;subscription.request(1);}
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if(buffer.remaining()>limit-bytes.size()) {subscription.cancel();result.completeExceptionally(new Unavailable("AI_RESPONSE_TOO_LARGE"));return;}
                byte[] data=new byte[buffer.remaining()];buffer.get(data);bytes.writeBytes(data);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) {result.completeExceptionally(new Unavailable("AI_UNAVAILABLE"));}
        public void onComplete() {result.complete(bytes.toByteArray());}
    }
}
