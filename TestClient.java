import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class TestClient {
    public static void main(String[] args) {
        try {
            String base = "http://ALB-723612005.us-east-1.elb.amazonaws.com";
            System.out.println("Base: " + base);
            String clean = base.replaceAll("/$", "");
            String start = URLEncoder.encode(Instant.now().minusSeconds(3600).toString(), StandardCharsets.UTF_8);
            String end = URLEncoder.encode(Instant.now().toString(), StandardCharsets.UTF_8);
            String url = clean + "/api/metrics/summary?roomId=1&userId=1000&startTime=" + start + "&endTime=" + end + "&topN=10";
            System.out.println("URL: " + url);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            System.out.println("Sending req...");
            HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP " + resp.statusCode());
            System.out.println(resp.body());
        } catch (Exception e) {
            System.err.println("Failed to fetch metrics API: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
