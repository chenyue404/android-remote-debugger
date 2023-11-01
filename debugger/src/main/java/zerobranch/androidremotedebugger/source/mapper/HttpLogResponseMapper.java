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
package zerobranch.androidremotedebugger.source.mapper;

import java.util.ArrayList;

import zerobranch.androidremotedebugger.source.local.Constants;
import zerobranch.androidremotedebugger.source.models.httplog.HttpLogModel;
import zerobranch.androidremotedebugger.source.models.httplog.HttpLogResponse;
import zerobranch.androidremotedebugger.source.models.httplog.QueryType;

public class HttpLogResponseMapper {

    public HttpLogModel map(HttpLogResponse response) {
        HttpLogModel httpLogModel = new HttpLogModel();
        httpLogModel.queryId = "id: " + response.queryId;
        httpLogModel.method = response.method;
        httpLogModel.time = Constants.defaultDateFormat.format(response.time);
        httpLogModel.code = response.code;
        httpLogModel.message = response.message;
        httpLogModel.fullStatus = response.code == -1 ? null : response.code + " " + response.message;
        httpLogModel.duration = response.duration == null ? null : response.duration + " ms";
        httpLogModel.bodySize = response.bodySize == null ? null : response.bodySize + " byte";
        httpLogModel.port = response.port;
        httpLogModel.ip = response.ip;
        httpLogModel.fullIpAddress = response.ip == null ? null : response.ip + ":" + response.port;
        httpLogModel.url = response.url;
        httpLogModel.errorMessage = response.errorMessage;
        httpLogModel.body = response.body;
        httpLogModel.queryType = QueryType.RESPONSE;

        httpLogModel.headers = new ArrayList<>();
        ArrayList<String> headers = response.headers;
        if (null != headers) {
            httpLogModel.headers.addAll(headers);
        }
        return httpLogModel;
    }
}
