package org.dromara.aivideo.infra.runninghub.client;

import java.io.IOException;
import java.net.http.HttpRequest;

/** RunningHub 受限 HTTP 传输边界。 */
interface RunningHubHttpTransport {

    Response send(HttpRequest request, int maxResponseBytes) throws IOException, InterruptedException;

    record Response(int statusCode, byte[] body) {
    }
}
