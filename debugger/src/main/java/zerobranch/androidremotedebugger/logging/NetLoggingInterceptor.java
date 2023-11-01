/*
 * Copyright 2020 Arman Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zerobranch.androidremotedebugger.logging;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;
import zerobranch.androidremotedebugger.AndroidRemoteDebugger;
import zerobranch.androidremotedebugger.source.managers.ContinuousDBManager;
import zerobranch.androidremotedebugger.source.mapper.HttpLogRequestMapper;
import zerobranch.androidremotedebugger.source.mapper.HttpLogResponseMapper;
import zerobranch.androidremotedebugger.source.models.httplog.HttpLogModel;
import zerobranch.androidremotedebugger.source.models.httplog.HttpLogRequest;
import zerobranch.androidremotedebugger.source.models.httplog.HttpLogResponse;

public class NetLoggingInterceptor implements Interceptor {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final AtomicInteger queryNumber = new AtomicInteger(0);
    private final HttpLogRequestMapper requestMapper = new HttpLogRequestMapper();
    private final HttpLogResponseMapper responseMapper = new HttpLogResponseMapper();
    private HttpLogger httpLogger;

    public NetLoggingInterceptor() {
    }

    public NetLoggingInterceptor(HttpLogger httpLogger) {
        this.httpLogger = httpLogger;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        if (!AndroidRemoteDebugger.isEnable()) {
            return chain.proceed(chain.request());
        }

        HttpLogRequest logRequest = new HttpLogRequest();
        HttpLogResponse logResponse = new HttpLogResponse();

        logRequest.time = System.currentTimeMillis();

        Request request = chain.request();
        RequestBody requestBody = request.body();

        logRequest.method = request.method();
        logRequest.url = request.url().toString();
        logRequest.port = String.valueOf(request.url().port());

        Headers headers = request.headers();
        logRequest.headers = new ArrayList<>();
        for (int i = 0, count = headers.size(); i < count; i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            if (!"Content-Type".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                logRequest.headers.add(name + ": " + value);
            }
        }

        if (logRequest.headers.isEmpty()) {
            logRequest.headers = null;
        }

        if (requestBody != null) {
            if (requestBody.contentType() != null) {
                MediaType contentType = requestBody.contentType();
                if (contentType != null) {
                    logRequest.requestContentType = contentType.toString();
                }
            }

            logRequest.bodySize = String.valueOf(requestBody.contentLength());

            Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);

            Charset charset = UTF8;
            MediaType contentType = requestBody.contentType();
            if (contentType != null) {
                charset = contentType.charset(UTF8);
            }

            if (charset != null) {
                logRequest.body = buffer.readString(charset);
            }
        }

        logRequest.queryId = String.valueOf(queryNumber.incrementAndGet());
        logResponse.queryId = logRequest.queryId;

        try {
            InetAddress address = InetAddress.getByName(new URL(request.url().toString()).getHost());
            logRequest.ip = address.getHostAddress();
        } catch (Exception ignored) {
            // ignore
        } finally {
            HttpLogModel logModel = requestMapper.map(logRequest);
            if (AndroidRemoteDebugger.isEnable()) {
                logRequest.id = getDataBase().addHttpLog(logModel);
                onReceiveLog(logModel);
            }
        }

        logResponse.time = System.currentTimeMillis();
        logResponse.method = logRequest.method;
        logResponse.port = logRequest.port;
        logResponse.ip = logRequest.ip;
        logResponse.url = logRequest.url;

        long startTime = System.currentTimeMillis();

        Response response;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
            logResponse.errorMessage = e.getMessage();

            HttpLogModel logModel = responseMapper.map(logResponse);
            if (AndroidRemoteDebugger.isEnable()) {
                getDataBase().addHttpLog(logModel);
                onReceiveLog(logModel);
            }

            throw e;
        }

        long endTime = System.currentTimeMillis();

        logResponse.duration = String.valueOf(endTime - startTime);
        logResponse.time = endTime;
        logResponse.code = response.code();
        logResponse.message = response.message();

        Headers responseHeaders = response.headers();
        logResponse.headers = new ArrayList<>();
        for (int i = 0, count = responseHeaders.size(); i < count; i++) {
            String name = responseHeaders.name(i);
            String value = responseHeaders.value(i);
            logResponse.headers.add(name + ": " + value);
        }

        if (logResponse.headers.isEmpty()) {
            logResponse.headers = null;
        }

        ResponseBody responseBody = response.body();
        if (HttpHeaders.promisesBody(response) && responseBody != null) {
            long responseContentLength = responseBody.contentLength();

            BufferedSource source = responseBody.source();
            source.request(Long.MAX_VALUE);
            Buffer buffer = source.getBuffer();

            if ("gzip".equalsIgnoreCase(responseHeaders.get("Content-Encoding"))) {
                try (GzipSource gzippedResponseBody = new GzipSource(buffer.clone())) {
                    buffer = new Buffer();
                    buffer.writeAll(gzippedResponseBody);
                }
            }

            Charset charset = UTF8;
            MediaType contentType = responseBody.contentType();
            if (contentType != null) {
                charset = contentType.charset(UTF8);
            }

            if (buffer.size() != 0) {
                logResponse.bodySize = String.valueOf(buffer.size());
            }

            if (responseContentLength != 0 && charset != null) {
                logResponse.body = buffer.clone().readString(charset);
            }
        }

        HttpLogModel logModel = responseMapper.map(logResponse);
        if (AndroidRemoteDebugger.isEnable()) {
            getDataBase().addHttpLog(logModel);
            onReceiveLog(logModel);
        }

        return response;
    }

    private void onReceiveLog(HttpLogModel logModel) {
        if (httpLogger != null) {
            httpLogger.log(logModel);
        }
    }

    private ContinuousDBManager getDataBase() {
        return ContinuousDBManager.getInstance();
    }

    public interface HttpLogger {
        void log(HttpLogModel httpLogModel);
    }

    private void log(String str) {
        Log.d("NetLoggingInterceptor", str);
    }
}
