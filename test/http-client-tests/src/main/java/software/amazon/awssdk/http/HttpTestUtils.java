/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.http;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.nio.charset.StandardCharsets.UTF_8;
import static software.amazon.awssdk.utils.StringUtils.isBlank;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.reactivex.Flowable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;
import software.amazon.awssdk.utils.BinaryUtils;

public class HttpTestUtils {
    private HttpTestUtils() {
    }

    public static WireMockServer createSelfSignedServer() {
        URL selfSignedJks = SdkHttpClientTestSuite.class.getResource("/selfSigned.jks");

        return new WireMockServer(wireMockConfig()
                                      .dynamicHttpsPort()
                                      .keystorePath(selfSignedJks.toString())
                                      .keystorePassword("changeit")
                                      .keystoreType("jks")
        );
    }

    public static KeyStore getSelfSignedKeyStore() throws Exception {
        URL selfSignedJks = SdkHttpClientTestSuite.class.getResource("/selfSigned.jks");
        KeyStore keyStore = KeyStore.getInstance("jks");
        try (InputStream stream = selfSignedJks.openStream()) {
            keyStore.load(stream, "changeit".toCharArray());
        }

        return keyStore;
    }

    public static CompletableFuture<byte[]> sendGetRequest(int serverPort, SdkAsyncHttpClient client) {
        return sendRequest(serverPort, client, SdkHttpMethod.GET);
    }

    public static CompletableFuture<byte[]> sendHeadRequest(int serverPort, SdkAsyncHttpClient client) {
        return sendRequest(serverPort, client, SdkHttpMethod.HEAD);
    }

    private static CompletableFuture<byte[]> sendRequest(int serverPort,
                                                         SdkAsyncHttpClient client,
                                                         SdkHttpMethod httpMethod) {
        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                                                       .method(httpMethod)
                                                       .protocol("https")
                                                       .host("127.0.0.1")
                                                       .port(serverPort)
                                                       .build();
        return sendRequest(client, request);
    }

    public static CompletableFuture<byte[]> sendRequest(SdkAsyncHttpClient client, SdkHttpFullRequest request) {
        ByteArrayOutputStream responsePayload = new ByteArrayOutputStream();
        AtomicBoolean responsePayloadReceived = new AtomicBoolean(false);
        return client.execute(AsyncExecuteRequest.builder()
                                                 .responseHandler(new SdkAsyncHttpResponseHandler() {
                                                         @Override
                                                         public void onHeaders(SdkHttpResponse headers) {
                                                         }

                                                         @Override
                                                         public void onStream(Publisher<ByteBuffer> stream) {
                                                             Flowable.fromPublisher(stream).forEach(b -> {
                                                                 responsePayloadReceived.set(true);
                                                                 responsePayload.write(BinaryUtils.copyAllBytesFrom(b));
                                                             });
                                                         }

                                                         @Override
                                                         public void onError(Throwable error) {
                                                         }
                                                     })
                                                 .request(request)
                                                 .requestContentPublisher(new EmptyPublisher())
                                                 .build())
                     .thenApply(v -> responsePayloadReceived.get() ? responsePayload.toByteArray() : null);
    }

    public static SdkHttpContentPublisher createProvider(String body) {
        Stream<ByteBuffer> chunks = splitStringBySize(body).stream()
                                                           .map(chunk -> ByteBuffer.wrap(chunk.getBytes(UTF_8)));
        return new SdkHttpContentPublisher() {

            @Override
            public Optional<Long> contentLength() {
                return Optional.of(Long.valueOf(body.length()));
            }

            @Override
            public void subscribe(Subscriber<? super ByteBuffer> s) {
                s.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        chunks.forEach(s::onNext);
                        s.onComplete();
                    }

                    @Override
                    public void cancel() {

                    }
                });
            }
        };
    }

    public static Collection<String> splitStringBySize(String str) {
        if (isBlank(str)) {
            return Collections.emptyList();
        }
        ArrayList<String> split = new ArrayList<>();
        for (int i = 0; i <= str.length() / 1000; i++) {
            split.add(str.substring(i * 1000, Math.min((i + 1) * 1000, str.length())));
        }
        return split;
    }
}
